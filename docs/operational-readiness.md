# Operational readiness

These procedures do not deploy the application. Run tests with synthetic data.
External provider credentials and the target database have not been supplied.
A successful fixture test is not proof of delivery or recovery in that environment.

## First administrator

Use the normal MySQL profile with demo data disabled and exactly one application
instance. The development H2 profile is not suitable for persistent accounts.
Set these variables privately in the process environment or your secret manager:

- `DEMO_DATA_ENABLED=false`
- `BOOTSTRAP_ADMIN_ENABLED=true`
- `BOOTSTRAP_ADMIN_EMAIL`: your administrator's email
- `BOOTSTRAP_ADMIN_PASSWORD`: a unique password of at least 16 characters,
  at most 72 UTF-8 bytes (the BCrypt limit)

Start the application once. It stores a BCrypt hash with role ADMIN. It refuses
invalid credentials, demo mode, any existing administrator, or an email already
used by another account. It never promotes an existing patient or resets passwords.
After successful startup, stop the process, set `BOOTSTRAP_ADMIN_ENABLED=false`,
remove the two bootstrap credential variables, and restart. Confirm login and
access to `/admin/dashboard`. Future password recovery uses the email reset flow.
If provisioning was interrupted, check whether the account exists before retrying.
Do not run bootstrap concurrently on multiple instances.

## Secrets

Never place real keys in GitHub issues, PRs, CI artifacts, screenshots, or chat.
Use a secret manager or a private local environment file with mode 600. The
`.env.example` contains names only. Keep live keys separate from test keys.
Do not enable demo data, payment demo fallback, or a coupon in a real environment.
Set `APP_BASE_URL` to the intended origin; local testing may use localhost.
For an HTTPS deployment also set `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`.
Use encrypted database connections appropriate to the database provider.

## Backup and isolated restoration

Linux tools required: MySQL 8.4 client (`mysql`, `mysqldump`), Bash, gzip, coreutils.
Prepare a mode-600 client option file outside the repository:

```ini
[client]
host=YOUR_DATABASE_HOST
port=3306
user=YOUR_BACKUP_USER
password=YOUR_PASSWORD
```

Use the database provider's TLS settings and a least-privilege backup account.
Do not run schema changes during a backup. The script requires InnoDB tables and
uses a single transaction; it includes routines, triggers and events, but not
server accounts, grants, application secrets or uploaded files outside MySQL.

```sh
scripts/ops/mysql-backup.sh /private/source.cnf clinic /private/clinic.sql.gz
scripts/ops/mysql-restore-test.sh /private/isolated-target.cnf clinic_restore_test /private/clinic.sql.gz
```

The backup is a mode-600 gzip file, not encrypted. Encrypt it before transferring
or retaining it off-host, keep the decryption key separately, and define retention
and scheduled backups for the eventual deployment. The target must be a separate,
isolated MySQL server. Use a restricted restore account. Accept only your own
trusted dumps: SQL dumps are executable SQL, not passive data. Keep the event
scheduler off on the isolated server so restored events cannot run automatically.
The restore script requires a new name ending in `_restore_test` and refuses an
existing database. On failure it leaves partial data for inspection; it does not
retry, drop databases, or modify the original server.

After restoring, compare table counts, schema, constraints, and data checksums,
including `flyway_schema_history`. Start the application against that copy with
Hibernate validation and demo/bootstrap disabled. Disable mail, SMS, Stripe,
Ollama and DeepSeek credentials during recovery tests; use synthetic accounts for
login, booking and record checks. Record backup age and restore duration against
your accepted data-loss and downtime limits. Never upload a patient dump to CI.

CI exercises the scripts against synthetic MySQL 8.4 data, including Arabic and
Romanian text and a medical record. It compares every table checksum and schema,
checks overwrite refusal, and starts the application on the restored database
through `MySqlMigrationTest`. This does not replace a rehearsal with the target
schema. For a legacy database without Flyway history follow
[database migrations](database-migrations.md), including reviewed reconciliation
and explicit baselining. Do not enable automatic baselining.

## Provider acceptance tests still required

Run these in a separate environment with synthetic appointments and accounts.
Record outcome and provider event IDs without message bodies or credentials.

| Provider | Configuration | Acceptance evidence |
| --- | --- | --- |
| Stripe | Test secret, publishable key, matching webhook secret, reachable test webhook | Successful test checkout marks only its appointment paid; declined and cancelled attempts stay unpaid; duplicate webhook does not duplicate effects; test refund updates the right booking |
| Email | SMTP host/port/user/password, verified FROM, `MAIL_ENABLED=true`, correct APP_BASE_URL | Controlled inbox receives booking and reset messages; reset link points to the intended origin, works once and expires; provider reports delivery or bounce |
| SMS | Twilio SID/token/sender, `SMS_ENABLED=true` | One approved synthetic appointment notification reaches an owned/consenting test number; verify provider delivery status and failure handling |

Stripe test mode does not move money. SMTP and SMS acceptance can send real
messages and SMS may incur charges. Obtain recipient and spend approval before
sending. Never substitute test credentials for proof of real-provider delivery.
Mailpit workflow checks and locally signed Stripe fixtures remain useful automated
checks, but do not certify provider accounts or a live payment/refund.
