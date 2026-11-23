package com.resourceallocator.integration;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;

import com.resourceallocator.service.JobDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/**
 * Internal integration, end to end: a client registers, adds hardware and
 * runs jobs entirely through the HTTP API, with the controllers, services,
 * dispatcher, repositories and PostgreSQL all real. Each test checks that
 * what one endpoint does is seen correctly by the others.
 *
 * <p>Deleting a job is left out until issue #37 settles its behaviour.
 */
class JobLifecycleIT extends PostgresIntegrationTest {

    private static final String GPU_JOB = "{\"resourceType\": \"GPU\", "
            + "\"unitsRequested\": 1, \"minMemoryGb\": 40, "
            + "\"maxDurationHours\": 2}";

    @Autowired
    private JobDispatcher jobDispatcher;

    @Test
    void jobRunsQueuesBehindLimitAndStartsWhenFirstFinishes() {
        Client client = createClient("acme", 0, 1);
        long gpuId = addGpu(client.apiKey(), 80);

        // First job is dispatched straight away onto the only GPU.
        ApiResponse first = call(HttpMethod.POST, "/jobs", client.apiKey(),
                GPU_JOB);
        assertThat(first.status()).as(first.body()).isEqualTo(201);
        long firstId = first.longAt("$.jobId");
        assertThat(first.stringAt("$.status")).isEqualTo("RUNNING");
        assertThat(first.<List<Integer>>at("$.resourceIds"))
                .containsExactly((int) gpuId);
        assertThat(first.location()).hasPath("/jobs/" + firstId);

        // The resource list sees the job holding the GPU.
        ApiResponse resources = call(HttpMethod.GET, "/resources",
                client.apiKey(), null);
        assertThat(resources.stringAt("$.resources[0].status"))
                .isEqualTo("IN_USE");
        assertThat(resources.longAt("$.resources[0].runningJobId"))
                .isEqualTo(firstId);
        assertThat(resources.<Integer>at("$.summary.gpu.inUse")).isEqualTo(1);
        assertThat(resources.<Integer>at("$.summary.gpu.remaining")).isZero();

        // A second job waits: the organization's one-GPU limit is used up.
        ApiResponse second = call(HttpMethod.POST, "/jobs", client.apiKey(),
                GPU_JOB);
        long secondId = second.longAt("$.jobId");
        assertThat(second.stringAt("$.status")).isEqualTo("QUEUED");
        assertThat(second.<List<Integer>>at("$.resourceIds")).isEmpty();

        ApiResponse queue = call(HttpMethod.GET, "/jobs/queue?status=QUEUED",
                client.apiKey(), null);
        assertThat(queue.<List<Integer>>at("$.jobs[*].jobId"))
                .containsExactly((int) secondId);
        assertThat(queue.<Integer>at("$.jobs[0].queuePosition")).isEqualTo(1);
        assertThat(queue.<Boolean>at("$.jobs[0].blocked")).isTrue();
        assertThat(queue.stringAt("$.jobs[0].blockedReason"))
                .isEqualTo("EXCEEDS_USAGE_LIMIT");
        assertThat(queue.<Integer>at("$.summary.queued")).isEqualTo(1);
        assertThat(queue.<Integer>at("$.summary.running")).isEqualTo(1);

        // After the first job's two-hour lease, the second one takes over.
        jobDispatcher.dispatch(client.id(),
                Instant.now().plus(Duration.ofHours(3)));

        ApiResponse firstAfter = call(HttpMethod.GET, "/jobs/" + firstId,
                client.apiKey(), null);
        assertThat(firstAfter.stringAt("$.status")).isEqualTo("DONE");
        ApiResponse secondAfter = call(HttpMethod.GET, "/jobs/" + secondId,
                client.apiKey(), null);
        assertThat(secondAfter.stringAt("$.status")).isEqualTo("RUNNING");
        assertThat(secondAfter.<List<Integer>>at("$.resourceIds"))
                .containsExactly((int) gpuId);
    }

    @Test
    void jobWaitsForBusyResourceWhenUnderLimit() {
        Client client = createClient("acme", 0, 2);
        addGpu(client.apiKey(), 80);
        submitGpuJob(client.apiKey(), 1, 40);

        long waitingId = submitGpuJob(client.apiKey(), 1, 40)
                .longAt("$.jobId");

        ApiResponse queue = call(HttpMethod.GET, "/jobs/queue?status=QUEUED",
                client.apiKey(), null);
        assertThat(queue.longAt("$.jobs[0].jobId")).isEqualTo(waitingId);
        assertThat(queue.stringAt("$.jobs[0].blockedReason"))
                .isEqualTo("RESOURCES_IN_USE");
    }

    @Test
    void cpuJobIsPlacedOnlyOnResourceWithEnoughCores() {
        Client client = createClient("acme", 4, 0);
        addCpu(client.apiKey(), 64, 4);
        long bigCpu = addCpu(client.apiKey(), 64, 16);

        ApiResponse job = call(HttpMethod.POST, "/jobs", client.apiKey(),
                "{\"resourceType\": \"CPU\", \"unitsRequested\": 1, "
                        + "\"minMemoryGb\": 32, \"minCores\": 8, "
                        + "\"maxDurationHours\": 1}");

        assertThat(job.status()).as(job.body()).isEqualTo(201);
        assertThat(job.stringAt("$.status")).isEqualTo("RUNNING");
        assertThat(job.<List<Integer>>at("$.resourceIds"))
                .containsExactly((int) bigCpu);
    }

    @Test
    void busyResourceCannotBeTakenOfflineOrDeletedUntilJobFinishes() {
        Client client = createClient("acme", 0, 1);
        long gpuId = addGpu(client.apiKey(), 80);
        submitGpuJob(client.apiKey(), 1, 40);

        ApiResponse offline = call(HttpMethod.PATCH, "/resources/" + gpuId,
                client.apiKey(), "{\"online\": false}");
        assertThat(offline.status()).isEqualTo(409);
        ApiResponse delete = call(HttpMethod.DELETE, "/resources/" + gpuId,
                client.apiKey(), null);
        assertThat(delete.status()).isEqualTo(409);
        // Neither refused request changed the resource.
        assertThat(call(HttpMethod.GET, "/resources", client.apiKey(), null)
                .stringAt("$.resources[0].status")).isEqualTo("IN_USE");

        jobDispatcher.dispatch(client.id(),
                Instant.now().plus(Duration.ofHours(3)));
        ApiResponse offlineLater = call(HttpMethod.PATCH,
                "/resources/" + gpuId, client.apiKey(), "{\"online\": false}");

        assertThat(offlineLater.status()).as(offlineLater.body())
                .isEqualTo(200);
        assertThat(offlineLater.<Boolean>at("$.online")).isFalse();
        // A new job now has nowhere to go.
        long jobId = submitGpuJob(client.apiKey(), 1, 40).longAt("$.jobId");
        ApiResponse queue = call(HttpMethod.GET, "/jobs/queue?status=QUEUED",
                client.apiKey(), null);
        assertThat(queue.longAt("$.jobs[0].jobId")).isEqualTo(jobId);
        assertThat(queue.stringAt("$.jobs[0].blockedReason"))
                .isEqualTo("NO_MATCHING_RESOURCE");
    }

    @Test
    void bringingResourceBackOnlineDispatchesWaitingJob() {
        Client client = createClient("acme", 0, 1);
        long gpuId = addGpu(client.apiKey(), 80);
        call(HttpMethod.PATCH, "/resources/" + gpuId, client.apiKey(),
                "{\"online\": false}");
        long jobId = submitGpuJob(client.apiKey(), 1, 40).longAt("$.jobId");

        call(HttpMethod.PATCH, "/resources/" + gpuId, client.apiKey(),
                "{\"online\": true}");
        // Reading the job runs a dispatch pass.
        ApiResponse job = call(HttpMethod.GET, "/jobs/" + jobId,
                client.apiKey(), null);

        assertThat(job.stringAt("$.status")).isEqualTo("RUNNING");
        assertThat(job.<List<Integer>>at("$.resourceIds"))
                .containsExactly((int) gpuId);
    }
}
