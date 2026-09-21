#!/usr/bin/env bash
set -Eeuo pipefail
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
runtime="$repo/.local-demo"
mkdir -p "$runtime"
chmod 700 "$runtime"
pg_bin=${PERFORMANCE_PG_BIN:-}
if [[ -z "$pg_bin" ]]; then
  for candidate in "$runtime"/tools/usr/lib/postgresql/*/bin /usr/lib/postgresql/*/bin; do
    if [[ -x "$candidate/pg_ctl" ]]; then pg_bin=$candidate; fi
  done
fi
export LD_LIBRARY_PATH="$runtime/tools/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
command=${1:-start}
stop_process() {
  local pid_file=$1 expected=$2
  [[ -f "$pid_file" ]] || return 0
  local pid
  pid=$(cat "$pid_file")
  if [[ -r "/proc/$pid/cmdline" ]] && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -Fq "$expected"; then
    kill "$pid"
    for attempt in $(seq 1 20); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.25
    done
  fi
  rm -f "$pid_file"
}
if [[ "$command" == stop ]]; then
  stop_process "$runtime/frontend.pid" 'vite'
  stop_process "$runtime/backend.pid" 'easy-performance-management'
  if [[ -n "$pg_bin" && -f "$runtime/pg/PG_VERSION" ]]; then
    "$pg_bin/pg_ctl" -D "$runtime/pg" stop -m fast || true
  fi
  exit 0
fi
if [[ "$command" == status ]]; then
  curl -fsS http://127.0.0.1:8087/actuator/health
  printf '\nBrowser: http://localhost:5174\n'
  exit 0
fi
[[ "$command" == start ]] || { echo 'Usage: local-demo.sh start|stop|status' >&2; exit 2; }
if [[ -f "$runtime/backend.pid" ]] && kill -0 "$(cat "$runtime/backend.pid")" 2>/dev/null; then
  echo 'Demo is already running: http://localhost:5174'; exit 0
fi
for port in 8087 5174 55487; do
  if ss -ltnH "sport = :$port" | grep -q .; then
    echo "Port $port is already in use; existing process was not changed." >&2; exit 1
  fi
done
started_pg=false
startup_in_progress=true
cleanup_startup() {
  local exit_status=$?
  if [[ "$startup_in_progress" == true && "$exit_status" -ne 0 ]]; then
    stop_process "$runtime/frontend.pid" 'vite'
    stop_process "$runtime/backend.pid" 'easy-performance-management'
    if [[ "$started_pg" == true ]]; then
      "$pg_bin/pg_ctl" -D "$runtime/pg" stop -m fast >/dev/null 2>&1 || true
    fi
    echo 'Demo startup failed. Processes started by this attempt were stopped; logs and data were preserved.' >&2
  fi
}
trap cleanup_startup EXIT
if [[ -z "$pg_bin" ]]; then
  "$repo/scripts/prepare-local-postgres.sh"
  for candidate in "$runtime"/tools/usr/lib/postgresql/*/bin; do pg_bin=$candidate; done
fi
if [[ ! -f "$runtime/secrets.env" ]]; then
  umask 077
  printf 'PERFORMANCE_DEMO_DB_PASSWORD=%s\nPERFORMANCE_DEMO_JWT_SECRET=%s\n' "$(openssl rand -hex 24)" "$(openssl rand -hex 64)" > "$runtime/secrets.env"
fi
# This file is generated above, private, ignored by git, and never contains external credentials.
source "$runtime/secrets.env"
if [[ ! -f "$runtime/pg/PG_VERSION" ]]; then
  "$pg_bin/initdb" -D "$runtime/pg" -U performance_demo --auth-local=trust --auth-host=scram-sha-256 \
    --pwfile=<(printf '%s' "$PERFORMANCE_DEMO_DB_PASSWORD") > "$runtime/initdb.log"
fi
"$pg_bin/pg_ctl" -D "$runtime/pg" -l "$runtime/postgres.log" -o "-p 55487 -h 127.0.0.1 -k $runtime" start
started_pg=true
if ! "$pg_bin/psql" -h "$runtime" -p 55487 -U performance_demo -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='performance_demo'" | grep -qx 1; then
  "$pg_bin/createdb" -h "$runtime" -p 55487 -U performance_demo performance_demo
fi
(cd "$repo/backend" && ./gradlew bootJar --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m) > "$runtime/build.log" 2>&1
jar_file=$(find "$repo/backend/build/libs" -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -1)
[[ -n "$jar_file" ]] || { echo 'Backend jar missing; inspect .local-demo/build.log' >&2; exit 1; }
# Run an immutable copy: rebuilding build/libs must not alter a running JVM archive.
cp "$jar_file" "$runtime/easy-performance-management-runtime.jar"
jar_file="$runtime/easy-performance-management-runtime.jar"
# A clean environment prevents developer .env/external-service settings from entering the demo.
setsid env -i PATH="$PATH" HOME="$HOME" PERFORMANCE_DEMO_DB_PASSWORD="$PERFORMANCE_DEMO_DB_PASSWORD" \
  PERFORMANCE_DEMO_JWT_SECRET="$PERFORMANCE_DEMO_JWT_SECRET" \
  java -Xms128m -Xmx512m -jar "$jar_file" --spring.profiles.active=local-demo > "$runtime/backend.log" 2>&1 < /dev/null &
echo $! > "$runtime/backend.pid"
ready=false
for attempt in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8087/actuator/health >/dev/null 2>&1; then ready=true; break; fi
  if ! kill -0 "$(cat "$runtime/backend.pid")" 2>/dev/null; then break; fi
  sleep 1
done
[[ "$ready" == true ]] || { echo 'Startup failed; inspect .local-demo/backend.log' >&2; exit 1; }
# ApplicationRunner seed can complete shortly after Tomcat binds; wait for the account itself.
for attempt in $(seq 1 20); do
  if "$pg_bin/psql" -h "$runtime" -p 55487 -U performance_demo -d performance_demo -Atc "SELECT count(DISTINCT role) FROM user_account WHERE tenant_id='00000000-0000-0000-0000-000000000001' AND email LIKE 'dev-%@performance.dev'" | grep -qx 5; then break; fi
  sleep 1
done
"$pg_bin/psql" -h "$runtime" -p 55487 -U performance_demo -d performance_demo -v ON_ERROR_STOP=1 -f "$repo/scripts/local-demo-seed.sql" > "$runtime/seed.log"
(cd "$repo/frontend-vite" && exec setsid node node_modules/vite/bin/vite.js --host 0.0.0.0 --port 5174 --strictPort) > "$runtime/frontend.log" 2>&1 < /dev/null &
echo $! > "$runtime/frontend.pid"
frontend_ready=false
for attempt in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:5174/ >/dev/null 2>&1; then frontend_ready=true; break; fi
  if ! kill -0 "$(cat "$runtime/frontend.pid")" 2>/dev/null; then break; fi
  sleep 1
done
[[ "$frontend_ready" == true ]] || { echo 'Frontend startup failed; inspect .local-demo/frontend.log' >&2; exit 1; }
startup_in_progress=false
printf 'Local synthetic demo: http://localhost:5174\nAccounts: dev-hr-admin / dev-manager / dev-employee @performance.dev\nDemo password: dev\n'
