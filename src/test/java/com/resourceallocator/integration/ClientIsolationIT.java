package com.resourceallocator.integration;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Internal integration of client registration, API-key authentication and
 * per-organization data: a key issued by {@code POST /clients} must work
 * on every protected endpoint, and only for that organization's data.
 */
class ClientIsolationIT extends PostgresIntegrationTest {

    @Test
    void issuedKeyWorksAndMissingOrUnknownKeysAreRejected() {
        Client client = createClient("acme", 4, 4);

        assertThat(call(HttpMethod.GET, "/resources", client.apiKey(), null)
                .status()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/jobs/queue", client.apiKey(), null)
                .status()).isEqualTo(200);
        for (String path : List.of("/resources", "/jobs/queue", "/jobs/1")) {
            assertThat(call(HttpMethod.GET, path, null, null).status())
                    .as("no key on %s", path).isEqualTo(401);
            assertThat(call(HttpMethod.GET, path, "not-a-real-key", null)
                    .status()).as("bad key on %s", path).isEqualTo(401);
        }
        // An invalid key is rejected before the body is even validated.
        assertThat(call(HttpMethod.POST, "/jobs", "not-a-real-key", "{}")
                .status()).isEqualTo(401);
    }

    @Test
    void organizationCannotSeeOrTouchAnotherOrganizationsData() {
        Client acme = createClient("acme", 4, 4);
        Client globex = createClient("globex", 4, 4);
        long acmeGpu = addGpu(acme.apiKey(), 80);
        long acmeJob = submitGpuJob(acme.apiKey(), 1, 40).longAt("$.jobId");

        assertThat(call(HttpMethod.GET, "/jobs/" + acmeJob, globex.apiKey(),
                null).status()).isEqualTo(404);
        assertThat(call(HttpMethod.PATCH, "/resources/" + acmeGpu,
                globex.apiKey(), "{\"online\": false}").status())
                .isEqualTo(404);
        assertThat(call(HttpMethod.DELETE, "/resources/" + acmeGpu,
                globex.apiKey(), null).status()).isEqualTo(404);
        assertThat(call(HttpMethod.GET, "/resources?id=" + acmeGpu,
                globex.apiKey(), null).status()).isEqualTo(404);
        ApiResponse globexResources = call(HttpMethod.GET, "/resources",
                globex.apiKey(), null);
        assertThat(globexResources.<List<Object>>at("$.resources")).isEmpty();
        assertThat(call(HttpMethod.GET, "/jobs/queue", globex.apiKey(), null)
                .<List<Object>>at("$.jobs")).isEmpty();
    }

    @Test
    void jobIsNeverPlacedOnAnotherOrganizationsHardware() {
        Client acme = createClient("acme", 4, 4);
        Client globex = createClient("globex", 4, 4);
        addGpu(acme.apiKey(), 80);

        ApiResponse globexJob = submitGpuJob(globex.apiKey(), 1, 40);

        assertThat(globexJob.stringAt("$.status")).isEqualTo("QUEUED");
        ApiResponse queue = call(HttpMethod.GET, "/jobs/queue",
                globex.apiKey(), null);
        assertThat(queue.stringAt("$.jobs[0].blockedReason"))
                .isEqualTo("NO_MATCHING_RESOURCE");
    }

    @Test
    void organizationNamesAreUniqueAfterTrimming() {
        createClient("acme", 4, 4);

        ApiResponse duplicate = call(HttpMethod.POST, "/clients", null,
                "{\"orgName\": \"  acme  \", \"maxCpus\": 1, \"maxGpus\": 1}");

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void storesOnlyHashOfIssuedKey() {
        Client client = createClient("acme", 4, 4);

        String storedHash = jdbc.queryForObject(
                "SELECT api_key_hash FROM organization WHERE id = ?",
                String.class, client.id());

        assertThat(storedHash).hasSize(64).isNotEqualTo(client.apiKey());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization "
                + "WHERE api_key_hash = ?", Integer.class, client.apiKey()))
                .isZero();
    }
}
