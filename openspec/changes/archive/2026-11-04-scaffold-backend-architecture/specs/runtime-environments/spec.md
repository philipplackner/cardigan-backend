# Spec Delta

## Purpose

Defines how the service behaves in each environment: how the environment is chosen, where database settings come from, how the schema is handled, and where logs go, so that development convenience can never reach production by accident.

## ADDED Requirements

### Requirement: The environment is chosen explicitly
The service SHALL offer the profiles `dev` and `prod`, selected through `SPRING_PROFILES_ACTIVE`, and SHALL NOT activate any profile by default.

#### Scenario: Dev selected
- **WHEN** the service starts with `SPRING_PROFILES_ACTIVE=dev`
- **THEN** the `dev` profile is active and `prod` is not

#### Scenario: Nothing selected
- **WHEN** the service starts with no active profile
- **THEN** neither `dev` nor `prod` is active

### Requirement: Database settings come only from the environment
The service SHALL read its database connection from `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`, SHALL ship no default value for them, and SHALL NOT have connection details or secrets committed to the repository. Locally, the values MAY come from the git-ignored `.env` file. `DB_URL` SHALL be a JDBC URL with no credentials in it. The database role SHALL be allowed to create schemas.

#### Scenario: URL missing
- **WHEN** the service starts and `DB_URL` is missing
- **THEN** startup fails and the service does not begin serving requests

#### Scenario: Credentials missing
- **WHEN** the service starts against a Postgres that requires password authentication and `DB_USERNAME` or `DB_PASSWORD` is missing
- **THEN** startup fails and the service does not begin serving requests

#### Scenario: Pointing at another Postgres
- **WHEN** `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` point at a reachable Postgres database other than Supabase
- **THEN** the service starts and uses that database with no other configuration change

### Requirement: The service starts only with a reachable database
The service SHALL connect to its database during startup, and SHALL fail startup when the connection can't be made.

#### Scenario: Valid dev connection
- **WHEN** the service starts with the `dev` profile and valid credentials for the Supabase dev branch through the session pooler
- **THEN** startup completes without errors

#### Scenario: Database unreachable at startup
- **WHEN** the service starts and its database is unreachable or rejects the credentials
- **THEN** startup fails and the log states the connection problem without printing the password

### Requirement: Dev updates the schema from the application model
With the `dev` profile, the service SHALL create missing schemas, tables and columns from its data model at startup, SHALL preserve existing data, and SHALL NOT run migrations. Dropping existing tables and columns is a manual step. Hibernate may alter a column's type when the entity's type changes.

#### Scenario: New entity on dev
- **WHEN** the data model gains a table and the service restarts with the `dev` profile
- **THEN** the table exists in the database and existing rows in other tables are unchanged

#### Scenario: Restart with no model change
- **WHEN** the service restarts with the `dev` profile and the model is unchanged
- **THEN** all existing dev data is still present

#### Scenario: New column on dev
- **WHEN** an existing table's entity gains a column and the service restarts with the `dev` profile
- **THEN** the column exists and existing rows are unchanged

#### Scenario: No migrations on dev
- **WHEN** the service starts with the `dev` profile against an empty database
- **THEN** no Flyway history table exists

### Requirement: Prod never changes the schema from the application model
With the `prod` profile or no profile, the service SHALL change the schema only through migrations, and SHALL fail startup when the data model and the schema disagree. With no migrations present, startup SHALL still succeed as long as the model has no tables.

#### Scenario: No migrations and no model
- **WHEN** the service starts with the `prod` profile against an empty database while no migration files and no entities exist
- **THEN** startup completes and no application tables are created

#### Scenario: Migrations run on prod
- **WHEN** the service starts with the `prod` profile against an empty database while no migration files and no entities exist
- **THEN** the schemas `user_service` and `board_service` exist, Flyway's history table exists in `user_service`, and no other table exists

#### Scenario: Model disagrees with the schema
- **WHEN** the service starts with the `prod` profile and the data model has a table the schema lacks
- **THEN** startup fails and the schema is left unchanged by the data model

#### Scenario: No profile never updates the schema
- **WHEN** the service starts with no active profile and the data model has a table the schema lacks
- **THEN** startup fails and the table is not created

### Requirement: Logs go only to the console
In every profile, the service SHALL write logs as plain text to the console, and SHALL NOT write log files or send logs to an external service.

#### Scenario: Running service
- **WHEN** the service runs in any profile
- **THEN** its log lines appear on standard output in plain text, and it creates no log file
