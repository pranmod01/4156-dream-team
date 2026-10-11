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
3. Start the app with `mvn spring-boot:run`. Flyway will create the tables on startup.

If your setup differs (port, user, database, or password), create an `.env` file based on `.env.example`.

NOTE: dev_password is for local use only. Do not expose yout Postgres to external connections.
