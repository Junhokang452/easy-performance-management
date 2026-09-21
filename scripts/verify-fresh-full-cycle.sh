#!/usr/bin/env bash
# Fresh, isolated synthetic PostgreSQL; never loads developer .env or contacts Neon.
set -Eeuo pipefail
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
base="$repo/.local-demo"
run_dir="$base/fresh-$(date +%Y%m%d-%H%M%S)"
report="$repo/_workspace/full-cycle-20260907"
pg_bin="$base/tools/usr/lib/postgresql/18/bin"
jar_file="$repo/backend/build/libs/easy-performance-management-0.0.1-SNAPSHOT.jar"
if [[ ! -f "$jar_file" ]]; then
  mapfile -t jars < <(find "$repo/backend/build/libs" -maxdepth 1 -name '*.jar' ! -name '*-plain.jar')
  [[ ${#jars[@]} == 1 ]] || { echo 'Build one application JAR before verification.' >&2; exit 1; }
  jar_file=${jars[0]}
fi
[[ -x "$pg_bin/initdb" && -f "$base/secrets.env" ]] || { echo 'Prepare the isolated local demo first.' >&2; exit 1; }
for port in 55488 8088; do
  if ss -ltnH "sport = :$port" | grep -q .; then echo "Verification port $port is already in use." >&2; exit 1; fi
done
mkdir -p "$run_dir" "$report"
chmod 700 "$run_dir"
cp "$jar_file" "$run_dir/easy-performance-management-runtime.jar"
jar_file="$run_dir/easy-performance-management-runtime.jar"
source "$base/secrets.env"
export LD_LIBRARY_PATH="$base/tools/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
started_pg=false
backend_pid=''
cleanup() {
  local status=$?
  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then kill "$backend_pid"; wait "$backend_pid" 2>/dev/null || true; fi
  if [[ "$started_pg" == true ]]; then "$pg_bin/pg_ctl" -D "$run_dir/pg" stop -m fast >/dev/null 2>&1 || true; fi
  echo "Fresh runtime retained: $run_dir"
  exit "$status"
}
trap cleanup EXIT
"$pg_bin/initdb" -D "$run_dir/pg" -U performance_demo --auth-local=trust --auth-host=scram-sha-256 --pwfile=<(printf '%s' "$PERFORMANCE_DEMO_DB_PASSWORD") > "$run_dir/initdb.log"
"$pg_bin/pg_ctl" -D "$run_dir/pg" -l "$run_dir/postgres.log" -o "-p 55488 -h 127.0.0.1 -k $run_dir" start
started_pg=true
"$pg_bin/createdb" -h "$run_dir" -p 55488 -U performance_demo performance_demo
env -i PATH="$PATH" HOME="$HOME" PERFORMANCE_DEMO_DB_PASSWORD="$PERFORMANCE_DEMO_DB_PASSWORD" PERFORMANCE_DEMO_JWT_SECRET="$PERFORMANCE_DEMO_JWT_SECRET" \
 java -Xms128m -Xmx512m -jar "$jar_file" --spring.profiles.active=local-demo --server.port=8088 \
 --spring.datasource.url=jdbc:postgresql://127.0.0.1:55488/performance_demo > "$run_dir/backend.log" 2>&1 &
backend_pid=$!
ready=false
for attempt in $(seq 1 90); do
 if curl -fsS http://127.0.0.1:8088/actuator/health >/dev/null 2>&1; then ready=true; break; fi
 if ! kill -0 "$backend_pid" 2>/dev/null; then break; fi
 sleep 1
done
[[ "$ready" == true ]] || { tail -60 "$run_dir/backend.log"; exit 1; }
seeded=false
for attempt in $(seq 1 15); do
 if "$pg_bin/psql" -h "$run_dir" -p 55488 -U performance_demo -d performance_demo -Atc "SELECT count(*) FROM user_account WHERE email='dev-hr-admin@performance.dev'" | grep -qx 1; then seeded=true; break; fi
 sleep 1
done
[[ "$seeded" == true ]] || { echo 'Development account seed not ready'; exit 1; }
"$pg_bin/psql" -h "$run_dir" -p 55488 -U performance_demo -d performance_demo -v ON_ERROR_STOP=1 -f "$repo/scripts/local-demo-seed.sql" > "$run_dir/seed.log"
"$pg_bin/psql" -h "$run_dir" -p 55488 -U performance_demo -d performance_demo -Atc "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank" > "$report/fresh-migrations.txt"
python3 "$repo/scripts/verify-full-resources.py" --base-url http://127.0.0.1:8088 --output "$report/fresh-resources-api.json"
python3 "$repo/scripts/verify-full-program.py" --base-url http://127.0.0.1:8088 --output "$report/fresh-program-api.json"
python3 "$repo/scripts/verify-program-policy.py" --base-url http://127.0.0.1:8088 --output "$report/fresh-program-policy.json"
python3 "$repo/scripts/verify-program-revisions.py" --base-url http://127.0.0.1:8088 --fixture "$report/fresh-program-api.json" --output "$report/fresh-program-revisions.json"
python3 "$repo/scripts/verify-program-calculation-policies.py" --base-url http://127.0.0.1:8088 --output "$report/fresh-program-calculations.json"
cp "$run_dir/backend.log" "$report/fresh-boot.log"
echo 'Fresh PostgreSQL boot and full-cycle HTTP verification passed.'
