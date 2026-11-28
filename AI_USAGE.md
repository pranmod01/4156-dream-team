# AI Tool Usage Log

This file records every use of AI tools on this project, as the course
requires. Update it **at the end of every AI session**.

## Tools used

| Tool | Model(s) | How we got it | Used by | What we used it for |
|---|---|---|---|---|
| Claude Code (CLI) | Claude Opus 5.5 (`claude-opus-5-5`) | Personal Anthropic account (claude.ai) | Pranati Modumudi | Codebase orientation, design review, build/CI setup, project docs/scripts, PR review, endpoint implementation and tests |
| Claude Code (VS Code extension) | Claude Opus 5.5 (`claude-opus-5-5`) | Personal Anthropic account (claude.ai) | Mahsa Mohajeri | Job endpoint and dispatcher design, implementation and tests; PostgreSQL integration tests |
| Claude (claude.ai app, chat) | Claude Opus 5.5 | Personal Anthropic account (claude.ai) | Rob Stott | Generating the V2 migration, the delete resource endpoint and its tests, and resolving its merge conflicts |
| Claude Code | Claude Opus 5.5 | Personal Anthropic account (claude.ai) | Leila Erhili | Review fixes for the job queue (busy-resource check, RESOURCES_IN_USE), running the dispatcher before building the queue, and their tests |

Add a row for any other tool (ChatGPT, Copilot, Cursor, Gemini, etc.).


## Marking AI-generated code

Any file or code block written **or debugged** by AI must be marked, even if
we edited it by hand afterwards.

- **Whole file:** put this comment on the first line (after the `package`
  line in Java):
  ```java
  // AI-generated (Claude Code, Claude Opus 5.5)
  ```
- **Part of a file:** wrap it:
  ```java
  // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
  ...
  // END AI-generated
  ```
- **AI-debugged code:** `// AI-debugged (<tool>): <one-line description of the fix>`
- SQL / YAML / shell use the same text with `--` or `#` comments.
- This applies to test code as well.
- Every AI-generated file must also be listed under its session below.
- Run `grep -rn "AI-generated\|AI-edited\|AI-debugged" .` to find all
  marked code.

## Token totals

**Grand total, all tools, all members: 78,074,245 tokens** (as of 2026-11-27)

| Tool | Member | Input | Output | Cache write | Cache read | Total |
|---|---|---:|---:|---:|---:|---:|
| Claude Code | Pranati Modumudi | 638 | 259,477 | 797,757 | 33,310,340 | 34,368,212 |
| Claude Code | Mahsa Mohajeri | 348 | 167,721 | 997,933 | 17,621,247 | 18,787,249 |
| Claude (claude.ai app) | Rob Stott | 94 | 56,370 | 851,934 | 17,836,929 | 18,745,327 |
| Claude Code | Nicholas Keung | 196 | 71,161 | 315,766 | 5,786,334 | 6,173,457 |
| **All** | | **1,276** | **554,729** | **2,963,390** | **74,554,850** | **78,074,245** |

### How to get your token counts

- **Claude Code:** run `./scripts/ai-token-usage.sh` from the repo root (it
  reads your local logs in `~/.claude/projects/`). `/cost` inside a session
  also works on API-billed accounts.
- **ChatGPT / Gemini web chat:** these don't show token counts. Estimate as
  (characters in prompts + responses) ÷ 4 and mark the number as an estimate.
- **Copilot / Cursor:** check the usage page in your account settings; if it
  only shows requests, record the request count and note that.

"Cache read" tokens are context the model re-reads on each turn. They're
billed at a lower rate but are still tokens used, so include them in the total.

## Session log

Add new sessions at the bottom. Copy the template:

```markdown
### YYYY-MM-DD — <member> — <tool / model>
**Session ID:** <Claude Code session id, if any>
**Purpose:** <what you were trying to do>
**Requests (summary):**
- <general summary of what you asked the AI to do>
**What the AI did:** <summary>
**Files generated / modified by AI:** <paths, and whether whole file or part>
**How we used/checked the output:** <accepted, edited, rejected, tested how>
**Tokens:** input / output / cache write / cache read / total
```

---

### 2026-10-15 — Pranati Modumudi — Claude Code / Claude Opus 5.5
**Session ID:** `3154a290-a93f-466a-8d40-f7803cfc0ef0`
**Purpose:** Orient the AI on the codebase and proposal, set up AI usage
tracking, and finish the project's build/tooling setup.

**Requests (summary):**
- Read the codebase and our proposal's API spec, and summarize how they fit
  together.
- Create a file and script to track AI usage and token counts.
- List the remaining non-API setup work.
- Set up the Maven wrapper, Checkstyle/PMD/JaCoCo, and GitHub Actions CI.

**What the AI did:**
- Summarized the stack and schema, and listed where the proposal and schema
  don't match (no reservations table, no utilization data, no way to end a
  running job early, Add Job doesn't return assigned resource IDs, unclear
  whether resources are shared between orgs, ambiguous CPU limit units, no
  dispatcher).
- Created this log and a token-counting script.
- Added the missing Maven wrapper properties. Wired Checkstyle (pinned
  12.3.1, the newest that runs on Java 17) and PMD (pinned 7.28.0) into the
  build so violations fail it. Removed the `DesignForExtension` check, which
  conflicts with Spring proxies. Fixed existing Checkstyle violations.
  Cleaned up the JaCoCo config. Set JPA to validate against the Flyway
  schema. Added `.editorconfig` and a CI workflow. Updated the README.

**Files generated / modified by AI:**
- `AI_USAGE.md`: new, whole file
- `scripts/ai-token-usage.sh`: new, whole file
- `.github/workflows/ci.yml`: new, whole file
- `.editorconfig`: new, whole file
- `.mvn/wrapper/maven-wrapper.properties`: generated by `mvn wrapper:wrapper`
  (run by the AI)
- `src/main/java/com/resourceallocator/package-info.java`: new, whole file
- `src/main/java/com/resourceallocator/ResourceAllocatorApplication.java`:
  edited for Checkstyle (Javadoc, spaces, protected constructor)
- `src/test/java/com/resourceallocator/ResourceAllocatorApplicationTests.java`:
  edited (tabs to spaces)
- `pom.xml`: JaCoCo, Checkstyle, and PMD plugin sections
- `checkstyle.xml`: removed `DesignForExtension`
- `src/main/resources/application.yaml`, `src/test/resources/application.yaml`:
  `jpa` sections
- `README.md`: "Build, Test, and Style Checks" section; fixed `mvn` →
  `./mvnw` and a typo
- `.env.example`: typo fix

**How we used/checked the output:** Reviewed the design gaps as a team
discussion list. Ran `./mvnw clean verify` locally: 0 Checkstyle violations,
PMD passes, 1/1 tests pass, build succeeds.
**Tokens:** 88 / 33,564 / 67,993 / 3,400,497 / **3,502,142** (final, from
`./scripts/ai-token-usage.sh`)

### 2026-10-15 — Pranati Modumudi — Claude Code / Claude Opus 5.5
**Session ID:** `f7093d4f-43d7-4447-8307-277e0247936d`
**Purpose:** Implement the create client (#1) and get resources (#2)
endpoints.

**Requests (summary):**
- Explain where new endpoints belong in the project structure.
- Write the `POST /clients` and `GET /resources` endpoints with tests.

**What the AI did:**
- Implemented `GET /resources` (all of the caller's resources, online and
  offline, ordered by id) and `POST /clients` (no API key needed; returns a
  random 256-bit API key once and stores only its SHA-256 hash; 409 on a
  duplicate name, including concurrent duplicates).
- Wrote unit and API tests for both endpoints.

**Files generated / modified by AI:**
- `src/main/java/com/resourceallocator/controller/ClientController.java`: new, whole file
- `src/main/java/com/resourceallocator/service/ClientService.java`: new, whole file
- `src/main/java/com/resourceallocator/dto/CreateClientRequest.java`: new, whole file
- `src/main/java/com/resourceallocator/dto/CreateClientResponse.java`: new, whole file
- `src/main/java/com/resourceallocator/exception/ConflictException.java`: new, whole file
- `src/main/java/com/resourceallocator/controller/ResourceController.java`:
  `listResources` method (part); class Javadoc and imports updated
- `src/main/java/com/resourceallocator/service/ResourceService.java`:
  `listResources` method (part); class Javadoc and imports updated
- `src/main/java/com/resourceallocator/repository/ResourceRepository.java`:
  `findByOrganizationIdOrderByIdAsc` (part)
- `src/main/java/com/resourceallocator/repository/OrganizationRepository.java`:
  `existsByOrgName` (part)
- `src/main/java/com/resourceallocator/exception/ApiExceptionHandler.java`:
  `handleConflict` (part)
- `src/test/java/com/resourceallocator/service/ClientServiceTest.java`: new, whole file
- `src/test/java/com/resourceallocator/controller/ClientAndResourceListApiTest.java`:
  new, whole file
- `src/test/java/com/resourceallocator/service/ResourceServiceTest.java`:
  `listResourcesReturnsAllOfTheOrganizationsResources` test (part)
- `AI_USAGE.md`: this entry and the token totals

**How we used/checked the output:** Pranati Modumudi reviewed every line of
the generated code and tests on 2026-10-15 and accepted it without changes.
Ran `./mvnw verify`: 47/47 tests pass (13 new), 0 Checkstyle violations.
**Tokens:** 116 / 39,996 / 177,905 / 4,825,069 / **5,043,086** (final, from
`./scripts/ai-token-usage.sh f7093d4f-43d7-4447-8307-277e0247936d`).

### 2026-10-26 — Pranati Modumudi — Claude Code / Claude Opus 5.5
**Session ID:** `af5637d8-aeb4-48d3-bcbf-0afaab158dec`
**Purpose:** Address PR review comments on the create client and list
resources endpoints.

**Requests (summary):**
- Add an API test rejecting an organization name over 255 characters.
- Document that the 255-character limit is checked before the name is
  trimmed.
- Split the combined API test into one test class per controller.

**What the AI did:**
- Split `ClientAndResourceListApiTest` into `ClientApiTest` and
  `ResourceApiTest` (tests moved unchanged).
- Added `createClientRejectsNameOverMaxLength` (256 chars → 400) and
  `createClientAcceptsNameAtMaxLength` (255 chars → 201).
- Documented the length-check-before-trim ordering in the
  `CreateClientRequest` Javadoc and a comment in `ClientService`.

**Files generated / modified by AI:**
- `src/test/java/com/resourceallocator/controller/ClientApiTest.java`: new, whole file
- `src/test/java/com/resourceallocator/controller/ResourceApiTest.java`: new, whole file
- `src/test/java/com/resourceallocator/controller/ClientAndResourceListApiTest.java`:
  deleted (replaced by the two files above)
- `src/main/java/com/resourceallocator/dto/CreateClientRequest.java`:
  Javadoc paragraph (file already marked AI-generated)
- `src/main/java/com/resourceallocator/service/ClientService.java`: comment
  in `createClient` (file already marked AI-generated)
- `AI_USAGE.md`: this entry, the previous entry's final tokens, and the
  token totals

**How we used/checked the output:** Ran `./mvnw verify`: 49/49 tests pass
(2 new), 0 Checkstyle violations. Pending review by Pranati Modumudi.
**Tokens:** 44 / 12,774 / 40,723 / 1,428,328 / **1,481,869** (final, from
`./scripts/ai-token-usage.sh af5637d8-aeb4-48d3-bcbf-0afaab158dec`). 

### 2026-10-26 — Pranati Modumudi — Claude Code / Claude Opus 5.5
**Session ID:** `5c52e294-7473-4cde-ad13-ef2edb65b609`
**Purpose:** Check the project against the grading rubric, fill the README
and report gaps, and extend `GET /resources` to match the proposal's Get
Resource Status endpoint (#29).

**Requests (summary):**
- Check which rubric items are met on `main`, and whether each teammate
  has documented their AI use.
- Add the missing README sections (task tracking, third-party code,
  authentication, call order, side effects, usage examples, test tooling,
  style rulesets) and commit the coverage and style reports (PR #27).
- Close finished issues (#11, #13).
- Compare the proposal with the built endpoints, then extend
  `GET /resources` with filters, per-resource status, and a usage summary,
  and document the remaining deviations from the proposal.

**What the AI did:**
- Audited `main` against the rubric; ran `./mvnw verify` to measure
  coverage and style results.
- On `docs-readme-reports` (PR #27): wrote the README sections, fixed the
  documented add job route and error codes, verified every example
  response with a temporary MockMvc test (deleted afterwards), added
  `reports/`, removed `HELP.md`.
- Closed #11 and #13 with comments.
- On `pran-dev`: implemented filters (`id`, `resourceType`, `minMemoryGb`,
  `minCores`), `AVAILABLE`/`IN_USE`/`OFFLINE` status with the running job,
  and a per-type usage summary for `GET /resources`; wrote unit and API
  tests; rewrote its README section and added "Deviations from the
  Proposal".
- On `main`, after all PRs merged: added the "no AI use" line for Nick,
  noted in the README that `GET /resources` runs the dispatcher, and
  refreshed `reports/` and the coverage number.

**Files generated / modified by AI:**
- `README.md` (both branches): sections listed above
- `reports/` (PR #27): generated by `./mvnw verify`, copied by the AI
- `src/main/java/com/resourceallocator/dto/ResourceFilter.java`,
  `ResourceListResponse.java`, `ResourceStatus.java`,
  `ResourceStatusEntry.java`, `ResourceSummary.java`,
  `ResourceUsage.java`: new, whole files
- `src/main/java/com/resourceallocator/controller/RequestParams.java`:
  new, whole file (enum parsing moved from `JobQueueController`)
- `src/main/java/com/resourceallocator/controller/JobQueueController.java`:
  uses `RequestParams` (part)
- `src/main/java/com/resourceallocator/controller/ResourceController.java`:
  `listResources` (part, already marked)
- `src/main/java/com/resourceallocator/service/ResourceService.java`:
  `listResources` and its helpers; `JobRepository` constructor argument
- `src/main/java/com/resourceallocator/repository/JobRepository.java`:
  `findRunningAssignments` (part)
- `src/test/java/com/resourceallocator/service/ResourceServiceTest.java`:
  9 new tests and helpers (part)
- `src/test/java/com/resourceallocator/controller/ResourceApiTest.java`:
  5 new tests, 1 updated (file already marked)
- `src/test/java/com/resourceallocator/controller/ClientApiTest.java`:
  one assertion updated for the new response shape (file already marked)
- `AI_USAGE.md`: this entry, the previous entry's final tokens, and the
  token totals

**How we used/checked the output:** Each change was proposed as a plan and
approved by Pranati Modumudi before it was made. Ran `./mvnw clean verify`:
120/120 tests pass, 0 Checkstyle and 0 PMD violations, 93.7% branch
coverage.
**Tokens:** 238 / 96,938 / 352,117 / 15,390,971 / **15,840,264** (final, from
`./scripts/ai-token-usage.sh 5c52e294-7473-4cde-ad13-ef2edb65b609`).

### 2026-11-24 — Pranati Modumudi — Claude Code / Claude Opus 5.5
**Session ID:** `3c793557-e48a-4173-aac0-30036a4c39ee`
**Purpose:** Let registered clients change their usage limits without
conflicting with dispatches that are running at the same time.

**Requests (summary):**
- Allow clients to change `maxCpus`/`maxGpus` after registering.
- Make sure a lowered limit doesn't break running jobs or concurrent
  dispatches.
- Add unit and API tests.
- Run the static analysis bug finder (PMD) and style checker
  (Checkstyle), check whether main's new integration tests conflict, and
  bring the work up to date with main.
- Commit the feature and the class tests on separate branches (class
  tests are logged on the class-tests branch).

**What the AI did:**
- Added `PATCH /clients/me` (API key required). It locks the organization
  row (the same lock every dispatch takes), rejects with `409` a limit
  below a queued job's request, saves the new limits, then runs the
  dispatcher so a raised limit starts waiting jobs at once. Running jobs
  are never stopped.
- Changed `POST /jobs` to lock the organization before inserting the job
  and to check the limit on the locked row, not the copy loaded during
  authentication. This closes a race with limit changes.
- Added unit and API tests, documented the endpoint in the README, and
  ran `./mvnw verify` (all tests and Checkstyle pass).
- Ran PMD and Checkstyle: no findings on main code. Over test code
  (not checked by the build), PMD found nothing and Checkstyle found only
  missing Javadoc on test fields and test packages.
- Trial-merged onto main's integration tests: only this file conflicted.
  Pulled main and resolved it. The integration tests need Docker, which
  was not available locally, so they were left to CI.

**Files generated / modified by AI (as marked in the code):**
- `src/main/java/com/resourceallocator/dto/UpdateClientLimitsRequest.java` (new)
- `src/main/java/com/resourceallocator/dto/ClientResponse.java` (new)
- `src/main/java/com/resourceallocator/service/ClientService.java`
- `src/main/java/com/resourceallocator/controller/ClientController.java`
- `src/main/java/com/resourceallocator/service/JobService.java`
- `src/main/java/com/resourceallocator/model/Organization.java`: `setLimits` (part)
- `src/main/java/com/resourceallocator/repository/JobRepository.java`:
  `existsWithMoreUnitsThan` (part)
- `src/main/java/com/resourceallocator/auth/ApiKeyAuthConfig.java`:
  `/clients/me` path (part)
- `src/test/java/com/resourceallocator/service/ClientServiceTest.java`
- `src/test/java/com/resourceallocator/service/JobServiceTest.java`
- `src/test/java/com/resourceallocator/controller/ClientApiTest.java`
- `README.md`: `PATCH /clients/me` section, side-effects note

**Tokens:** 152 input / 76,205 output / 159,019 cache write / 8,265,475
cache read / **8,500,851 total**

### 2026-11-27 — Mahsa Mohajeri — Claude Code / Claude Opus 5.5
**Session ID:** `17496cea-b89c-4b5b-b05a-7d211b27d352`
**Purpose:** Run a stricter static analysis bug finder on the service
(issue #39), fix the real bugs it finds, and commit before/after reports.

**Requests (summary):**
- Review Pranati's PRs #43 and #44 and check which issues are still open.
- Check the static analysis requirement against the repo, then do it.

**What the AI did:**
- Ran SpotBugs (maximum effort, all confidence levels) with the
  FindSecBugs security rules, and PMD's `errorprone`, `multithreading`,
  `bestpractices`, `security` and `performance` categories, since the
  default PMD ruleset found nothing.
- Saved the raw "before" output (PMD: 7, SpotBugs: 18), sorted the
  findings into real bugs and false positives, and fixed the real ones:
  exceptions now keep their cause (PMD `PreserveStackTrace`), and three
  response records keep an unmodifiable copy of their list (SpotBugs
  `EI_EXPOSE_REP`/`EI_EXPOSE_REP2`).
- Excluded the reviewed false positives with a stated reason in
  `pmd-ruleset.xml` and `spotbugs-exclude.xml`, and added SpotBugs to
  `./mvnw verify` so CI fails on new findings.
- Added tests for the fixes, saved the "after" reports (both clean),
  refreshed the committed reports, and documented it all in the
  README's Static Analysis section. `./mvnw clean verify` passes: 155
  unit and API tests, 18 integration tests, no Checkstyle, PMD or
  SpotBugs findings.

**Files generated / modified by AI (as marked in the code):**
- `pom.xml`: SpotBugs plugin, PMD ruleset (part)
- `pmd-ruleset.xml` (new)
- `spotbugs-exclude.xml` (new)
- `src/main/java/com/resourceallocator/exception/InvalidRequestException.java`: cause constructor (part)
- `src/main/java/com/resourceallocator/exception/ConflictException.java`: cause constructor (part)
- `src/main/java/com/resourceallocator/controller/RequestParams.java`: pass the cause (part)
- `src/main/java/com/resourceallocator/service/ClientService.java`: `duplicateName` with cause (part)
- `src/main/java/com/resourceallocator/dto/JobResponse.java`, `JobQueueResponse.java`, `ResourceListResponse.java`: defensive copies (part)
- `src/test/java/com/resourceallocator/controller/RequestParamsTest.java` (new)
- `src/test/java/com/resourceallocator/dto/ResponseListCopyTest.java` (new)
- `src/test/java/com/resourceallocator/service/ClientServiceTest.java`: cause check (part)
- `.github/workflows/ci.yml`: upload the SpotBugs report (part)
- `README.md`: Static Analysis section, tool and report updates
- `reports/`: refreshed reports, `reports/static-analysis/` before/after

**Tokens:** 118 input / 38,721 output / 155,361 cache write / 4,149,714
cache read / **4,343,914 total** (as of this commit)

### 2026-10-19 — Mahsa Mohajeri — Claude Code / Claude Opus 5.5
**Session ID:** `c4a5740c-c79d-434b-908c-751f243c929c`
**Purpose:** Implement my endpoints: add job (#3), get job status (#4), and
delete job (#5).

**Requests (summary):**
- Implement the three job endpoints, including the dispatcher that starts
  queued jobs, with unit and API tests.
- Add Javadoc notes requested in the PR #20 review.
- Return 404 instead of 400 for a missing job in `GET /jobs/{jobId}`, to
  match the other endpoints, and update the tests and README.

**What the AI did:**
- Implemented `POST /jobs`, `GET /jobs/{id}`, and `DELETE /jobs/{id}`, plus
  a dispatcher that ends expired leases and starts queued jobs on free,
  matching resources within the usage limit (it runs on every job request
  and every 30 seconds, and locks the org row so two requests can't take
  the same resource). Added `NotFoundException` (404).
- Wrote unit tests (Mockito) and API tests (MockMvc against H2).
- Added the Javadoc notes from the PR #20 review (merged as PR #21).
- Changed `GET /jobs/{jobId}` to return 404 for a missing job and updated
  the tests and README.

**Files generated / modified by AI:**
- `src/main/java/com/resourceallocator/controller/JobController.java`: new, whole file
- `src/main/java/com/resourceallocator/service/JobService.java`: new, whole file
- `src/main/java/com/resourceallocator/service/JobDispatcher.java`: new, whole file
- `src/main/java/com/resourceallocator/service/JobDispatchScheduler.java`: new, whole file
- `src/main/java/com/resourceallocator/dto/AddJobRequest.java`: new, whole file
- `src/main/java/com/resourceallocator/dto/JobResponse.java`: new, whole file
- `src/main/java/com/resourceallocator/dto/DeleteJobResponse.java`: new, whole file
- `src/main/java/com/resourceallocator/exception/NotFoundException.java`: new, whole file
- `src/main/java/com/resourceallocator/repository/JobRepository.java`: five
  query methods (part)
- `src/main/java/com/resourceallocator/repository/OrganizationRepository.java`:
  `findByIdForUpdate` (part)
- `src/main/java/com/resourceallocator/exception/ApiExceptionHandler.java`:
  `handleNotFound` (part)
- `src/main/java/com/resourceallocator/ResourceAllocatorApplication.java`:
  `@EnableScheduling` (part)
- `src/test/java/com/resourceallocator/controller/JobApiTest.java`: new, whole file
- `src/test/java/com/resourceallocator/service/JobServiceTest.java`: new, whole file
- `src/test/java/com/resourceallocator/service/JobDispatcherTest.java`: new, whole file
- `src/test/java/com/resourceallocator/service/JobDispatchSchedulerTest.java`:
  new, whole file
- PR #21: Javadoc paragraphs in `JobDispatcher` and `JobService.deleteJob`
- `JobService.getJobStatus`: 404 for a missing job (part); matching updates
  to `JobServiceTest`, `JobApiTest`, and the `GET /jobs/{jobId}` section of
  `README.md`

**How we used/checked the output:** Ran `./mvnw verify`: 91/91 tests pass
(44 new), 0 Checkstyle and PMD violations, 100% line coverage on the new
classes. Opened PR #20, which CI passed and 2D-Cone reviewed and approved
before I merged it. The review's documentation suggestions went into PR #21.
After the 404 change, `./mvnw verify` passes with 105/105 tests.
**Tokens:** 138 / 82,749 / 584,120 / 9,875,158 / **10,542,165** (whole
session, 2026-10-19 to 2026-10-26, measured near the end. The session was
started from the parent folder `~/Projects/dream-team`, so
`./scripts/ai-token-usage.sh` doesn't find it; these counts use the
script's formula on that folder's session log.)

### 2026-10-15 to 2026-10-26 — Rob Stott — Claude (claude.ai app) / Claude Opus 5.5
**Session ID:** none (claude.ai chat, not Claude Code)

- **Tool:** Claude in the claude.ai app (chat, not Claude Code), model Claude Opus 5.5.
- **Where I got it:** claude.ai (Anthropic), through my personal Claude account.
- **How I used it:** to generate code that I then reviewed, built with `./mvnw verify`, and committed myself.

| Date | What was generated | Prompts (summary) | Files | Merged? | Tokens (input / output / cache write / cache read / **total**) |
|---|---|---|---|---|---|
| 2026-10-15 | Delete Job endpoint: exceptions, service, controller, response DTO, unit + API tests, and Checkstyle fixes | "walk me through Delete Job" | (not in repo) | No: a duplicate of Mahsa's Delete Job (#20), so it was dropped | 18 / 15,136 / 31,958 / 2,311,317 / **2,358,429** |
| 2026-10-19 | DB migration so a resource that finished jobs used can be deleted (`ON DELETE CASCADE` on `job_resource.resource_id`) | "walk me through the cascade migration" | `src/main/resources/db/migration/V2__cascade_job_resource_on_resource_delete.sql` | Yes (#22) | 18 / 6,406 / 12,049 / 2,631,203 / **2,649,676** |
| 2026-10-20 to 2026-10-24 | Delete Resource endpoint and tests: service method, controller method, response DTO, README section, unit + API tests | "walk me through Delete Resource" | `dto/DeleteResourceResponse.java`; `deleteResource` in `service/ResourceService.java` and `controller/ResourceController.java`; README `DELETE /resources/{resourceId}`; `test/.../controller/DeleteResourceApiTest.java`; delete tests in `test/.../service/ResourceServiceTest.java` | Yes (#25) | 28 / 21,728 / 355,706 / 5,403,506 / **5,780,968** |
| 2026-10-26 | Merge of main into the delete resource branch: resolved conflicts, updated the API test for the new `GET /resources` response, and added delete resource to the README's Call Order, Side Effects, Incorrect Usage, and Deviations sections | "walk me through fixing #25's conflicts" | `service/ResourceService.java`, `controller/ResourceController.java`, `test/.../service/ResourceServiceTest.java` (imports and class Javadoc); `test/.../controller/DeleteResourceApiTest.java`; `README.md` | Yes (#25) | 30 / 13,100 / 452,221 / 7,490,903 / **7,956,254** |
| | | | | **Total** | 94 / 56,370 / 851,934 / 17,836,929 / **18,745,327** |

### 2026-10-17 to 2026-10-22 — Leila Erhili — Claude Code / Claude Opus 5.5
**Purpose:** Address review feedback on the job queue endpoint (PR #16) and
keep the queue consistent with the dispatcher.

**What the AI did (from commits `dd08a25`, `bb8a98e`):**
- Made the job queue's blocked check exclude resources held by running jobs,
  and added the `RESOURCES_IN_USE` blocked reason.
- Changed the job queue to run the dispatcher before building its response,
  and reused `JobRepository`'s busy-resource query.
- Added unit and API tests for both changes.

**Files generated / modified by AI (as marked in the code):**
- `src/main/java/com/resourceallocator/dto/BlockedReason.java`:
  `RESOURCES_IN_USE` (part)
- `src/main/java/com/resourceallocator/repository/ResourceRepository.java`:
  query method (part)
- `src/main/java/com/resourceallocator/service/JobQueueService.java`:
  busy-resource check (part)
- `src/test/java/com/resourceallocator/service/JobQueueServiceTest.java`:
  tests (part)
- `src/test/java/com/resourceallocator/controller/ResourceAndJobQueueApiTest.java`:
  tests (part)

Her original endpoints (#7, #8, #10, commit `48da62c`) carry no AI markers.

### 2026-11-23 — Mahsa Mohajeri — Claude Code / Claude Opus 5.5
**Session ID:** `ff141416-53be-40fc-8fb7-5e0869cd7d86`
**Purpose:** Add integration tests (#35).

**Requests (summary):**
- Explain what the integration-test issue needs and whether it depends on
  the other open issues.
- Help get Docker Desktop working locally.
- Set up integration tests against a real PostgreSQL and write them.

**What the AI did:**
- Added Testcontainers and the Maven Failsafe plugin, so `*IT` classes run
  in `./mvnw verify` against PostgreSQL 16 in Docker, while `./mvnw test`
  stays H2-only.
- Wrote 18 integration tests: Flyway migrations, constraints, and the V2
  cascade on PostgreSQL; end-to-end job lifecycle and client isolation over
  real HTTP; and concurrency tests for the dispatcher's organization lock.
  Delete-job tests wait for #37.
- Checked the concurrency tests can fail: with the organization lock
  temporarily removed, 3 of the 4 failed. The change was reverted.
- Updated the README testing section and proposal-deviation note, and CI
  uploads the failsafe reports.

**Files generated / modified by AI:**
- `src/test/java/com/resourceallocator/integration/PostgresIntegrationTest.java`: new, whole file
- `src/test/java/com/resourceallocator/integration/PostgresSchemaIT.java`: new, whole file
- `src/test/java/com/resourceallocator/integration/JobLifecycleIT.java`: new, whole file
- `src/test/java/com/resourceallocator/integration/ClientIsolationIT.java`: new, whole file
- `src/test/java/com/resourceallocator/integration/ConcurrencyIT.java`: new, whole file
- `pom.xml`: Testcontainers dependencies and Failsafe plugin (part)
- `.github/workflows/ci.yml`: failsafe report upload (part)
- `README.md`: Build/Tests section and the testing deviation note (part)
- `AI_USAGE.md`: this entry and my row in the tools table (part)

**How we used/checked the output:** Ran `./mvnw clean verify` locally:
152/152 tests pass (18 new integration tests), 0 Checkstyle and PMD
violations.
**Tokens:** 92 / 46,251 / 258,452 / 3,596,375 / **3,901,170** (measured
2026-11-23 near the end of the session. The session was started from the
parent folder `~/Projects/dream-team`, so `./scripts/ai-token-usage.sh`
doesn't find it; these counts use the script's formula on that folder's
session log.)
