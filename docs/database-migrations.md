# Database migrations

MySQL uses Flyway scripts in `src/main/resources/db/migration/mysql` and Hibernate
`ddl-auto=validate` in local, Docker, and cloud configurations. V1 covers all 15
mapped entities, foreign keys, one-to-one uniqueness, doctor/day uniqueness, and
the activity-log appointment index. Hibernate no longer updates MySQL schemas.
H2 dev and test profiles disable Flyway and keep their existing Hibernate DDL.

## New database

Create an empty UTF-8 MySQL database and configure the datasource. Start the
application. Flyway applies V1 before Hibernate validates the mappings. A second
startup verifies migration checksums and applies only new versioned migrations.
Do not run the legacy `schema.sql` file. It is replaced by the versioned migration.

## Existing database created by Hibernate

Automatic baselining is disabled. A nonempty database without Flyway history
fails startup instead of silently adopting an unknown schema.

1. Back up the database and restore it to a separate staging database.
2. Compare its tables, column types, nullability, indexes, and foreign keys with
   V1. Existing `schema.sql` installations can differ from the entity mappings.
   Reconcile those differences on the staging copy with reviewed ALTER scripts.
   Do not recreate tables or truncate longer clinical text to match V1.
3. Once the schema matches, use the Flyway CLI with the MySQL driver and V1
   location to run an explicit `baseline` at version `1`. Supply the datasource
   URL, user, and password through environment variables or a local config file.
4. Start the application against staging with `ddl-auto=validate`. Verify login,
   booking, payments, reports, and record counts. Hibernate validation does not
   replace the index/constraint comparison in step 2.
5. Review the tested ALTER and baseline steps before applying them to the backed
   up deployment database during a maintenance window.

Never enable `baseline-on-migrate` globally. V1 creates a new schema and must not
be executed on existing clinic tables. For later schema changes, add V2, V3, etc.
Do not edit applied migrations. MySQL DDL can commit implicitly, so restore a
backup or use a reviewed forward migration after a partially applied failure.

## Verification

`./mvnw test` runs the H2 suite. The opt-in `MySqlMigrationTest` starts the full
application against an empty MySQL database, validates the entity schema, then
runs migration again and checks that an inserted row survives. CI supplies a
disposable MySQL 8.4 service for this test. To run it locally, set
`MIGRATION_DB_URL`, `MIGRATION_DB_USER`, and `MIGRATION_DB_PASSWORD`, then run
`./mvnw -Dtest=MySqlMigrationTest test` against a disposable empty database.
