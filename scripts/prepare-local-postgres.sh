#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
runtime="$repo/.local-demo"
mkdir -p "$runtime/packages" "$runtime/tools"
command -v apt-get >/dev/null || { echo 'Set PERFORMANCE_PG_BIN to an installed PostgreSQL bin directory.' >&2; exit 1; }
pg_package=$(apt-cache search '^postgresql-[0-9]+$' | awk '{print $1}' | sort -V | tail -1)
[[ -n "$pg_package" ]] || { echo 'No PostgreSQL package available in the package cache.' >&2; exit 1; }
pg_version=${pg_package#postgresql-}
cd "$runtime/packages"
# Download/extract user-space tools only. No sudo, system service, or system package changes.
apt-get download "$pg_package" "postgresql-client-$pg_version" libpq5 liburing2
for archive in ./*.deb; do dpkg-deb -x "$archive" "$runtime/tools"; done
printf 'PostgreSQL tools ready: %s\n' "$runtime/tools/usr/lib/postgresql/$pg_version/bin"
