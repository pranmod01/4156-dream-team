# Resource Allocator API

This project implements a simple resource allocation service. Organizations can register with the service, list the available resources (CPUs and GPUs), set usage limits, and take specific resources offline. Other clients can then request specific resources for their jobs, and the job will be added to the queue. 

## Requirements
- Java 17
    - Install from https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html
- Maven 3.9.5
    - Install from https://maven.apache.org/download.cgi
- PostgreSQL 17.10
    - Install from https://www.postgresql.org/download/

## To Run Database Locally
1. Install PostgreSQL and start it on port 5432.
2. Run the database setup script using `./scripts/setup-db.sh` (for Windows, `PGUSER=postgres ./scripts/setup-db.sh`).
    - Creates the user called `resource_allocator` and with password `dev_password`
    - Creates a database called `resource_allocator_db`
    - Alternatively, set your own username, password, and database name. Set these in a `.env` file
3. Start the app with `./mvnw spring-boot:run`. Flyway will create the tables on startup.

If your setup differs (port, user, database, or password), create an `.env` file based on `.env.example`.

NOTE: dev_password is for local use only. Do not expose your Postgres to external connections.

## Build, Test, and Style Checks
Use the Maven wrapper (`./mvnw`, or `mvnw.cmd` on Windows) so everyone runs the same Maven version.

- `./mvnw verify` runs everything CI runs: Checkstyle, tests, the JaCoCo coverage report, and PMD. The build fails on any Checkstyle or PMD violation.
- `./mvnw test` runs the tests only (tests use an in-memory H2 database, so Postgres isn't needed).
- `./mvnw checkstyle:check` / `./mvnw pmd:check` run one style tool on its own.

Reports:
- Coverage: `target/site/jacoco/index.html`
- Checkstyle: `target/checkstyle-result.xml`
- PMD: `target/pmd.xml`

CI (`.github/workflows/ci.yml`) runs `./mvnw verify` on JDK 17 for every push and PR to `main`, and uploads these reports as a build artifact.
