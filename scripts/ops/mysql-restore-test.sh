#!/usr/bin/env bash
# Only restore dumps from mysql-backup.sh that you trust into an isolated server.
set -euo pipefail
umask 077
if [[ $# != 3 ]]; then
  echo 'Usage: mysql-restore-test.sh CLIENT_CONFIG DATABASE_restore_test INPUT.sql.gz' >&2
  exit 2
fi
config=$(realpath "$1")
database=$2
input=$3
[[ -f "$config" && $(stat -c %a "$config") == 600 ]] || { echo 'Client config must be a regular file with mode 600' >&2; exit 2; }
[[ "$database" =~ ^[a-zA-Z0-9_]+_restore_test$ ]] || { echo 'Target name must end in _restore_test' >&2; exit 2; }
[[ -f "$input" ]] || { echo 'Backup file not found' >&2; exit 2; }
gzip -t "$input"
# CREATE without IF NOT EXISTS refuses ANY existing database, including an empty one.
mysql --defaults-extra-file="$config" -e "CREATE DATABASE \`$database\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
# A failed restore deliberately leaves the partial database for investigation.
gzip -dc "$input" | mysql --defaults-extra-file="$config" --database="$database"
echo 'Restore completed. Validate schema, records, and application behavior before accepting the backup.'
