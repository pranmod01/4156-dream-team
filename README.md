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
    - `400 Bad Request` -- Also returned if `minCores` is missing for a CPU job or set for a GPU job. This is also returned if `unitsRequested` exceeds the organization's usage limit for that resource type.


### GET /jobs/{jobId}
- Returns a job's current status, including when it started, when its lease ends, and which resources it has been assigned.
- Before responding, the server dispatches any queued jobs that can now run, so the returned status is up to date.
- Authentication: required
- Path Parameters:
    - jobId (integer, required): The ID of the job, as returned when the job was created.

- Responses:
    - `200 OK` -- Returns the job's information.
        ```
        {
            "jobId": 1,
            "status": "RUNNING",
            "resourceType": "CPU",
            "unitsRequested": 1,
            "submittedAt": "2026-10-22T01:19:54.460571Z",
            "startedAt": "2026-10-22T01:19:54.512034Z",
            "leaseEnd": "2026-10-22T04:19:54.512034Z",
            "resourceIds": [3]
        }
        ```
    - `404 Not Found` -- The organization has no job with the given ID.


### DELETE /jobs/{jobId}
- Permanently deletes a job that is not currently running (i.e., `QUEUED` or `DONE`), along with any of its resource assignments.
- Running jobs cannot be deleted.
- Authentication: required
- Path Parameters:
    - jobId (integer, required): The ID of the job to delete.

- Responses:
    - `200 OK` -- Job deleted successfully. Returns the ID of the deleted job.
        ```
        {
            "deletedJobId": 1
        }
        ```
    - `404 Not Found` -- The organization has no job with the given ID.
    - `409 Conflict` -- The job is currently running and cannot be deleted.


### GET /jobs/queue
- Lists the organization's jobs in queue order (oldest submission first), with optional filters.
- Queued jobs include their position in the queue and, if they cannot run yet, why they are blocked. Also returns a count of jobs in each status.
- Authentication: required
- Query Parameters (all optional; values are case-insensitive):
    - status (string): Only return jobs in this state. One of `QUEUED`, `RUNNING`, `DONE`.
    - resourceType (string): Only return jobs requesting this resource type. One of `CPU`, `GPU`.
    - start (ISO-8601 timestamp): Only return running or done jobs whose run overlaps a window starting at this time. Queued jobs are always included.
    - end (ISO-8601 timestamp): Only return running or done jobs whose run overlaps a window ending at this time. Must not be before `start`.
    - limit (integer): Maximum number of jobs to return. Must be positive.
- Example Request: `GET /jobs/queue?status=queued&resourceType=CPU&limit=10`

- Responses:
    - `200 OK` -- Returns the matching jobs and a summary of job counts by status. `queuePosition` is null for jobs that are not queued, and `blockedReason` is one of `NO_MATCHING_RESOURCE` or `EXCEEDS_USAGE_LIMIT` when `blocked` is true.
        ```
        {
            "jobs": [
                {
                    "jobId": 2,
                    "status": "QUEUED",
                    "requiredResources": {
                        "resourceType": "CPU",
                        "unitsRequested": 1,
                        "minMemoryGb": 64,
                        "minCores": 4,
                        "maxDurationHours": 3
                    },
                    "submittedAt": "2026-10-22T01:19:54.460571Z",
                    "startedAt": null,
                    "leaseEnd": null,
                    "queuePosition": 1,
                    "blocked": true,
                    "blockedReason": "NO_MATCHING_RESOURCE"
                }
            ],
            "summary": {
                "queued": 1,
                "running": 0,
                "done": 0
            }
        }
        ```
    - `400 Bad Request` -- Also returned if `status` or `resourceType` is not a recognized value, a timestamp is malformed, `end` is before `start`, or `limit` is not positive.


### POST /resources
- Registers a new CPU or GPU resource for the organization. If registering a CPU, `cores` is required. Otherwise, `cores` must be null.
- Resources that are online can be assigned to queued jobs.
- Authentication: required
- Request Body:
    ```
    {
        "resourceType": "CPU",
        "memoryGb": 64,
        "cores": 8,
        "online": true
    }
    ```
    - resourceType (string, required): Type of resource. One of `CPU`, `GPU`.
    - memoryGb (integer, required): Amount of memory on the resource. Must be positive.
    - cores (integer): Number of cores on a CPU resource. Must be positive. Must be null for a GPU.
    - online (boolean, required): Whether the resource is available to run jobs.

- Responses:
    - `201 Created` -- Resource created successfully. Returns the resource's information.
        ```
        {
            "id": 1,
            "resourceType": "CPU",
            "memoryGb": 64,
            "cores": 8,
            "online": true
        }
        ```
    - `400 Bad Request` -- Also returned if `cores` is missing for a CPU resource or set for a GPU resource.


### GET /resources
- Lists the organization's resources with their current status, and summarizes the organization's usage against its limits.
- Before responding, the server runs the dispatcher (ending expired leases and starting queued jobs that can now run), so statuses are up to date.
- Authentication: required
- Query Parameters (all optional):
    - id (integer): Only return the resource with this ID.
    - resourceType (string): Only return resources of this type. One of `CPU`, `GPU` (case-insensitive).
    - minMemoryGb (integer): Only return resources with at least this much memory. Must be positive.
    - minCores (integer): Only return CPUs with at least this many cores. Must be positive. GPUs have no cores, so this excludes every GPU.
- Example Request: `GET /resources?resourceType=gpu&minMemoryGb=64`

- Responses:
    - `200 OK` -- Returns the matching resources, ordered by ID, and a usage summary.
        ```
        {
            "resources": [
                {
                    "id": 1,
                    "resourceType": "CPU",
                    "memoryGb": 64,
                    "cores": 8,
                    "online": true,
                    "status": "IN_USE",
                    "runningJobId": 3
                },
                {
                    "id": 2,
                    "resourceType": "GPU",
                    "memoryGb": 80,
                    "cores": null,
                    "online": false,
                    "status": "OFFLINE",
                    "runningJobId": null
                }
            ],
            "summary": {
                "cpu": { "limit": 4, "inUse": 1, "remaining": 3, "freeResources": 0 },
                "gpu": { "limit": 1, "inUse": 0, "remaining": 1, "freeResources": 0 }
            }
        }
        ```
        - `status` is `AVAILABLE` (online and not used by a job), `IN_USE` (held by the running job `runningJobId`), or `OFFLINE`.
        - `summary` always covers all of the organization's resources, whichever filters are applied. For each type, `limit` is the organization's usage limit, `inUse` is the units held by running jobs, `remaining` is how many more units the organization may start (`limit - inUse`, never negative), and `freeResources` is how many online resources no job is using. `remaining` can be smaller than `freeResources` when the usage limit, not the hardware, is the constraint.
    - `400 Bad Request` -- Also returned if `resourceType` is not recognized, or `minMemoryGb` or `minCores` is not positive.
    - `404 Not Found` -- `id` was given, but the organization has no resource with that ID.


### PATCH /resources/{resourceId}
- Takes one of the organization's resources online or offline. 
- If a resource is in use, taking it offline will fail. The endpoint will block any new dispatches, check the job queue for any finished jobs, and then attempt to take the resource offline. 
- Authentication: required
- Path Parameters:
    - resourceId (integer, required): The ID of the resource to update.
- Request Body:
    ```
    {
        "online": false
    }
    ```
    - online (boolean, required): The new online state of the resource.

- Responses:
    - `200 OK` -- Resource updated successfully. Returns the updated resource's information.
        ```
        {
            "id": 1,
            "resourceType": "CPU",
            "memoryGb": 64,
            "cores": 8,
            "online": false
        }
        ```
    - `404 Not Found` -- Returned if there is no resource with an ID associated with the organization.
    - `409 Conflict` -- Returned if attempting to take a resource offline while it is still running a job.


## Deviations from the Proposal
- **Get Resource Status (`GET /resources`)** does not report current utilization, free time slots, or a `RESERVED` status, and does not take a time window. These depend on reservations of future time, which we did not build: a job either runs as soon as resources are free or waits in the queue. Everything else in the proposal is supported: filters by ID, type, and minimum specs; per-resource status and the job using it; and the organization's usage against its limits.
- **Get Job Status (`GET /jobs/{jobId}`)** returns `404 Not Found` for an unknown job instead of the proposed `400 Bad Request`, to match Delete Job and Update Resource.
- **Update Resource (`PATCH /resources/{resourceId}`)** returns `404 Not Found` for an unknown resource instead of the proposed `400 Bad Request`, to match Delete Job and the usual meaning of the status codes. It also returns `409 Conflict` when taking a resource offline while a running job holds it, which the proposal did not cover, so that a running job never loses its resource.
- **Testing and tooling:** the proposal listed Testcontainers with PostgreSQL for integration tests, and Docker. Instead, the API tests run the full application against an in-memory H2 database, so the test suite and CI need neither Docker nor a running database. The database schema is still created by the same Flyway migrations. There is no Dockerfile; the app is run with `./mvnw spring-boot:run` against a local PostgreSQL.
