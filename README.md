# Resource Allocator API

This project implements a simple resource allocation service. Organizations can register with the service, list the available resources (CPUs and GPUs), set usage limits, and take specific resources offline. Other clients can then request specific resources for their jobs, and the job will be added to the queue. 

## Requirements
- Java 17
    - Install from https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html
- PostgreSQL 17.10
    - Install from https://www.postgresql.org/download/

Maven: This project uses a Maven 3.9.16 through the Maven Wrapper. A separate Maven installation is not required.

## To Set Up and Run Locally
1. Install PostgreSQL and start it on port 5432.
2. Run the database setup script using `./scripts/setup-db.sh` (for Windows, `PGUSER=postgres ./scripts/setup-db.sh`).
    - Creates the user called `resource_allocator` and with password `dev_password`
    - Creates a database called `resource_allocator_db`
    - Alternatively, set your own username, password, and database name. Set these in a `.env` file
3. Start the app with `./mvnw spring-boot:run`. Flyway will create the tables on startup.

If your setup differs (port, user, database, or password), create an `.env` file based on `.env.example`.

NOTE: dev_password is for local use only. Do not expose your Postgres to external connections.

## Build, Test, and Style Checks
Use the Maven wrapper (`./mvnw`, or `mvnw.cmd` on Windows) to automatically download and use this project's configured Maven version.

- `./mvnw verify` runs everything CI runs: Checkstyle, tests, the JaCoCo coverage report, and PMD. The build fails on any Checkstyle or PMD violation.
- `./mvnw test` runs the tests only (tests use an in-memory H2 database, so Postgres isn't needed).
- `./mvnw checkstyle:check` / `./mvnw pmd:check` run one style tool on its own.

Reports:
- Coverage: `target/site/jacoco/index.html`
- Checkstyle: `target/checkstyle-result.xml`
- PMD: `target/pmd.xml`

CI (`.github/workflows/ci.yml`) runs `./mvnw verify` on JDK 17 for every push and PR to `main`, and uploads these reports as a build artifact.

## API Endpoints

### Common Responses
- `400 Bad Request` -- The request is malformed or fails validation.
- `401 Unauthorized` -- Authentication is missing or invalid.
- `403 Forbidden` -- The authenticated organization does not have the necessary permissions.

### POST /clients
- Creates a new client, allowing a new organization to register resources and set usage limits
- Request Body:
    ```
    {
        "orgName": "name",
        "maxCpus": 0,
        "maxGpus": 0
    }
    ```
    - orgName (string, required): Unique organization name, max 255 characters
    - maxCpus (integer, required): Maximum number of CPU units an organization can have running simultaneously. Must be nonnegative.
    - maxGpus (integer, required): Maximum number of GPU units an organization can have running simultaneously. Must be nonnegative.

- Responses:
    - `201 Created` -- Organization created successfully. Returns organization information with API key.
    ```
    {
        "id": 1,
        "orgName": "name",
        "maxCpus": 1,
        "maxGpus": 1,
        "createdAt": "2026-10-22T01:19:54.460571Z",
        "apiKey": "fy992aNbxo1342oAd7O9FBDFd73QBtDJ1hWYFzOLsUs"
    }
    ```
    - `409 Conflict` -- Returns conflict if the name is not unique


### POST /jobs/addJob
- Creates a job, specifying the minimum CPU or GPU resources required. If CPUs are requested, `minCores` is required. Otherwise, `minCores` must be null. The endpoint also checks if this job is allowed to be run under the current usage limits. 
- Authentication: required
- Request Body: 
    ```
    {
        "resourceType": "CPU",
        "unitsRequested": 1,
        "minMemoryGb": 64,
        "minCores": 4,
        "maxDurationHours": 3
    }
    ```
    - resourceType (required)
    - unitsRequested (integer, required): The number of CPU or GPU units required. Must be nonnegative.
    - minMemoryGb (integer, required): The minimum amount of memory needed for the unit. Must be nonnegative.
    - minCores (integer): The number of cores required for a CPU request. Must be null if requesting GPU.
    - maxDurationHours (integer, required): A lease on the maximum amount of time this job can hold the resource. Must be nonnegative.

- Responses:
    - `201 Created` -- Job created successfully. Returns job information, along with its current status, when the job was started, and associated resource IDs.
        ```
        {
            "jobId": 1,
            "status": "QUEUED",
            "resourceType": "CPU",
            "unitsRequested": 1,
            "submittedAt": "2026-10-22T01:19:54.460571Z",
            "startedAt": null,
            "leaseEnd": null,
            "resourceIds": []
        }
        ```