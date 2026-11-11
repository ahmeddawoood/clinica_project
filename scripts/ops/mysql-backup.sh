#!/usr/bin/env bash
# Credentials belong in a mode-600 MySQL client option file, never in arguments.
set -euo pipefail
umask 077
if [[ $# != 3 ]]; then
  echo 'Usage: mysql-backup.sh CLIENT_CONFIG DATABASE OUTPUT.sql.gz' >&2
  exit 2
fi
config=$(realpath "$1")
database=$2
output=$3
[[ -f "$config" && $(stat -c %a "$config") == 600 ]] || { echo 'Client config must be a regular file with mode 600' >&2; exit 2; }
[[ "$database" =~ ^[a-zA-Z0-9_]+$ ]] || { echo 'Invalid database name' >&2; exit 2; }
[[ ! -e "$output" ]] || { echo 'Output already exists' >&2; exit 2; }
# Refuse engines for which --single-transaction cannot provide a consistent backup.
non_transactional=$(mysql --defaults-extra-file="$config" --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$database' AND table_type='BASE TABLE' AND engine <> 'InnoDB'")
[[ "$non_transactional" == 0 ]] || { echo 'Backup requires InnoDB tables' >&2; exit 2; }
tmp=$(mktemp "${output}.tmp.XXXXXX")
trap 'rm -f "$tmp"' EXIT
mysqldump --defaults-extra-file="$config" --single-transaction --quick \
  --no-tablespaces --set-gtid-purged=OFF --hex-blob --routines --events --triggers \
  --skip-add-drop-table "$database" | gzip > "$tmp"
gzip -t "$tmp"
# Hard-link publication refuses an existing destination even if created concurrently.
ln -T "$tmp" "$output"
echo 'Backup completed. Keep it encrypted and outside the application host.'
