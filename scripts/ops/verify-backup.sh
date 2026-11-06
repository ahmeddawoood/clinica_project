#!/usr/bin/env bash
# CI only: the source is a migrated, disposable fixture database.
set -euo pipefail
umask 077
config=$(realpath "$1")
source_db=clinic_migration
target_db=clinic_restore_test
ops=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
client=(mysql --defaults-extra-file="$config" --default-character-set=utf8mb4 --batch --skip-column-names)
"${client[@]}" "$source_db" <<'SQL'
INSERT INTO app_user (id,email,password,role,enabled) VALUES
(9001,'backup-patient@example.test','fixture','PATIENT',1),
(9002,'backup-doctor@example.test','fixture','DOCTOR',1);
INSERT INTO patient (id,first_name,last_name,user_id) VALUES (9001,'أحمد','Ștefan',9001);
INSERT INTO doctor (id,first_name,last_name,user_id) VALUES (9002,'Maria','Test',9002);
INSERT INTO medical_record (patient_id,doctor_id,title,treatment,created_at)
VALUES (9001,9002,'Backup fixture','Unicode العربية țș and newline\nsecond line',NOW(6));
SQL
"$ops/mysql-backup.sh" "$config" "$source_db" "$work/backup.sql.gz"
[[ $(stat -c %a "$work/backup.sql.gz") == 600 ]]
if "$ops/mysql-backup.sh" "$config" "$source_db" "$work/backup.sql.gz"; then
  echo 'FAIL: backup overwrote an existing file' >&2; exit 1
fi
if "$ops/mysql-restore-test.sh" "$config" "$source_db" "$work/backup.sql.gz"; then
  echo 'FAIL: restore accepted source database name' >&2; exit 1
fi
"$ops/mysql-restore-test.sh" "$config" "$target_db" "$work/backup.sql.gz"
if "$ops/mysql-restore-test.sh" "$config" "$target_db" "$work/backup.sql.gz"; then
  echo 'FAIL: restore accepted an existing database' >&2; exit 1
fi
# Compare every table's persisted data, including Flyway history and text encoding.
for table in $("${client[@]}" -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$source_db' ORDER BY table_name"); do
  before=$("${client[@]}" -e "CHECKSUM TABLE \`$source_db\`.\`$table\` EXTENDED" | cut -f2)
  after=$("${client[@]}" -e "CHECKSUM TABLE \`$target_db\`.\`$table\` EXTENDED" | cut -f2)
  [[ "$before" != NULL && "$before" == "$after" ]] || { echo "Checksum mismatch: $table" >&2; exit 1; }
done
# Compare schema, indexes and foreign keys independently of row checksums.
for database in "$source_db" "$target_db"; do
  mysqldump --defaults-extra-file="$config" --no-data --skip-comments \
    --no-tablespaces --set-gtid-purged=OFF "$database" > "$work/$database.sql"
done
diff -u "$work/$source_db.sql" "$work/$target_db.sql"
echo 'PASS: restore preserves all table checksums, schema and Flyway history; overwrite guards pass.'
