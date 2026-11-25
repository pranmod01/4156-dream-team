package com.resourceallocator.integration;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Concurrent requests against real PostgreSQL row locks. The dispatcher
 * serializes work per organization by locking the organization row, and
 * these tests check that the lock actually holds under parallel HTTP
 * requests. H2 locks differently, so the H2-based tests cannot show this.
 */
class ConcurrencyIT extends PostgresIntegrationTest {

    private static final int THREADS = 8;

    private static final int RACE_ROUNDS = 10;

    @Test
    void parallelSubmissionsNeverDoubleBookOneGpu() throws Exception {
        Client client = createClient("acme", 0, THREADS);
        long gpuId = addGpu(client.apiKey(), 80);

        List<ApiResponse> responses = runTogether(THREADS,
                () -> submitGpuJob(client.apiKey(), 1, 40));

        assertThat(responses).extracting(ApiResponse::status)
                .as(responses.toString()).containsOnly(201);
        assertThat(countJobs(client.id(), "RUNNING")).isEqualTo(1);
        assertThat(countJobs(client.id(), "QUEUED")).isEqualTo(THREADS - 1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM job_resource WHERE resource_id = ?",
                Integer.class, gpuId)).isEqualTo(1);
    }

    @Test
    void parallelSubmissionsNeverExceedUsageLimit() throws Exception {
        Client client = createClient("acme", 0, 2);
        for (int i = 0; i < THREADS; i++) {
            addGpu(client.apiKey(), 80);
        }

        runTogether(THREADS, () -> submitGpuJob(client.apiKey(), 1, 40));

        // Plenty of GPUs, but the limit of two must still hold.
        assertThat(countJobs(client.id(), "RUNNING")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_resource",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void takingGpuOfflineWhileJobIsSubmittedNeverLeavesJobOnOfflineGpu()
            throws Exception {
        for (int round = 0; round < RACE_ROUNDS; round++) {
            Client client = createClient("org-" + round, 0, 1);
            long gpuId = addGpu(client.apiKey(), 80);

            List<ApiResponse> responses = runTogether(List.of(
                    () -> call(HttpMethod.PATCH, "/resources/" + gpuId,
                            client.apiKey(), "{\"online\": false}"),
                    () -> submitGpuJob(client.apiKey(), 1, 40)));
            ApiResponse patch = responses.get(0);
            ApiResponse job = responses.get(1);

            assertThat(job.status()).as(job.body()).isEqualTo(201);
            boolean online = jdbc.queryForObject(
                    "SELECT is_online FROM resource WHERE id = ?",
                    Boolean.class, gpuId);
            int assignments = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM job_resource WHERE resource_id = ?",
                    Integer.class, gpuId);
            if (patch.status() == 200) {
                // The GPU went offline first, so the job had to wait.
                assertThat(online).isFalse();
                assertThat(assignments).isZero();
                assertThat(countJobs(client.id(), "QUEUED")).isEqualTo(1);
            } else {
                // The job got the GPU first, so going offline was refused.
                assertThat(patch.status()).as(patch.body()).isEqualTo(409);
                assertThat(online).isTrue();
                assertThat(assignments).isEqualTo(1);
                assertThat(countJobs(client.id(), "RUNNING")).isEqualTo(1);
            }
        }
    }

    @Test
    void parallelRegistrationsOfOneNameCreateExactlyOneOrganization()
            throws Exception {
        List<ApiResponse> responses = runTogether(THREADS,
                () -> call(HttpMethod.POST, "/clients", null,
                        "{\"orgName\": \"acme\", \"maxCpus\": 1, "
                                + "\"maxGpus\": 1}"));

        assertThat(responses).extracting(ApiResponse::status)
                .as(responses.toString())
                .containsOnly(201, 409)
                .containsOnlyOnce(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization",
                Integer.class)).isEqualTo(1);
    }

    private int countJobs(final long organizationId, final String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM job "
                + "WHERE organization_id = ? AND job_status = ?",
                Integer.class, organizationId, status);
    }

    private static List<ApiResponse> runTogether(final int copies,
            final Callable<ApiResponse> task) throws Exception {
        List<Callable<ApiResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < copies; i++) {
            tasks.add(task);
        }
        return runTogether(tasks);
    }

    // Starts every task at the same moment and returns their results in
    // the order given.
    private static List<ApiResponse> runTogether(
            final List<Callable<ApiResponse>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ApiResponse>> futures = new ArrayList<>();
            for (Callable<ApiResponse> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();
            List<ApiResponse> results = new ArrayList<>();
            for (Future<ApiResponse> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
