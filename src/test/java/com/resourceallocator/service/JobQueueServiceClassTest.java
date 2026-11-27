package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;

import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.dto.AddJobRequest;
import com.resourceallocator.dto.AddResourceRequest;
import com.resourceallocator.dto.BlockedReason;
import com.resourceallocator.dto.JobQueueEntry;
import com.resourceallocator.dto.JobQueueFilter;
import com.resourceallocator.dto.JobQueueResponse;
import com.resourceallocator.dto.JobStatusCounts;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.OrganizationRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class tests for {@link JobQueueService}: reads the queue again after
 * each change to the organization's jobs and resources, with the real
 * dispatcher and the in-memory test database, and checks that positions,
 * blocked reasons and counts follow. Each test runs in a transaction that
 * is rolled back afterwards.
 */
@SpringBootTest
@Transactional
class JobQueueServiceClassTest {

    private static final JobQueueFilter ALL =
            new JobQueueFilter(null, null, null, null, null);

    private static final Duration HOUR = Duration.ofHours(1);

    @Autowired
    private JobQueueService jobQueueService;

    @Autowired
    private JobService jobService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private JobDispatcher jobDispatcher;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = organizationRepository.saveAndFlush(new Organization(
                "acme", ApiKeyHasher.hash("key"), 4, 2));
    }

    @Test
    void positionsAndReasonsFollowDeletesAndNewResources() {
        Long first = submitGpuJob(1);
        Long second = submitGpuJob(1);
        Long third = submitGpuJob(1);
        JobQueueResponse start = queue(ALL);
        assertThat(start.jobs()).extracting(JobQueueEntry::queuePosition)
                .containsExactly(1, 2, 3);
        assertThat(start.jobs()).extracting(JobQueueEntry::blockedReason)
                .containsOnly(BlockedReason.NO_MATCHING_RESOURCE);

        jobService.deleteJob(organization, first);
        JobQueueResponse afterDelete = queue(ALL);
        assertThat(afterDelete.jobs()).extracting(JobQueueEntry::jobId)
                .containsExactly(second, third);
        assertThat(afterDelete.jobs())
                .extracting(JobQueueEntry::queuePosition)
                .containsExactly(1, 2);

        // Adding a GPU does not dispatch, but reading the queue does.
        resourceService.addResource(organization,
                new AddResourceRequest(ResourceType.GPU, 40, null, true));
        JobQueueResponse afterGpu = queue(ALL);
        JobQueueEntry running = afterGpu.jobs().get(0);
        JobQueueEntry waiting = afterGpu.jobs().get(1);
        assertThat(running.status()).isEqualTo(JobStatus.RUNNING);
        assertThat(running.queuePosition()).isNull();
        assertThat(running.blocked()).isFalse();
        assertThat(waiting.queuePosition()).isEqualTo(1);
        assertThat(waiting.blockedReason())
                .isEqualTo(BlockedReason.RESOURCES_IN_USE);
        assertThat(afterGpu.summary())
                .isEqualTo(new JobStatusCounts(1, 1, 0));
    }

    @Test
    void jobHeldByTheLimitStartsWhenTheRunningJobFinishes() {
        for (int i = 0; i < 3; i++) {
            resourceService.addResource(organization,
                    new AddResourceRequest(ResourceType.GPU, 40, null, true));
        }
        Long big = submitGpuJob(2);
        Long small = submitGpuJob(1);
        JobQueueEntry held = entry(queue(ALL), small);
        assertThat(held.blockedReason())
                .isEqualTo(BlockedReason.EXCEEDS_USAGE_LIMIT);

        jobDispatcher.dispatch(organization.getId(),
                Instant.now().plus(HOUR.multipliedBy(2)));
        JobQueueResponse after = queue(ALL);

        assertThat(entry(after, big).status()).isEqualTo(JobStatus.DONE);
        assertThat(entry(after, small).status()).isEqualTo(JobStatus.RUNNING);
        assertThat(after.summary()).isEqualTo(new JobStatusCounts(0, 1, 1));
    }

    @Test
    void filtersKeepEachJobsPositionInTheFullQueue() {
        Long cpuJob = jobService.addJob(organization,
                new AddJobRequest(ResourceType.CPU, 1, 16, 4, 1)).jobId();
        Long firstGpu = submitGpuJob(1);
        Long secondGpu = submitGpuJob(1);

        JobQueueResponse gpuOnly = queue(new JobQueueFilter(
                null, ResourceType.GPU, null, null, null));
        assertThat(gpuOnly.jobs()).extracting(JobQueueEntry::jobId)
                .containsExactly(firstGpu, secondGpu);
        assertThat(gpuOnly.jobs()).extracting(JobQueueEntry::queuePosition)
                .containsExactly(2, 3);
        assertThat(gpuOnly.summary()).isEqualTo(new JobStatusCounts(3, 0, 0));

        JobQueueResponse firstOnly = queue(new JobQueueFilter(
                null, null, null, null, 1));
        assertThat(firstOnly.jobs()).extracting(JobQueueEntry::jobId)
                .containsExactly(cpuJob);
    }

    @Test
    void timeWindowDropsFinishedJobsOutsideItButKeepsQueuedOnes() {
        Long finished = submitGpuJob(1);
        resourceService.addResource(organization,
                new AddResourceRequest(ResourceType.GPU, 40, null, true));
        // Start the job as if three hours ago; its one-hour lease has
        // ended, so the next read of the queue marks it done.
        jobDispatcher.dispatch(organization.getId(),
                Instant.now().minus(HOUR.multipliedBy(3)));
        Long queued = submitGpuJob(2);
        assertThat(entry(queue(ALL), finished).status())
                .isEqualTo(JobStatus.DONE);

        JobQueueResponse recent = queue(new JobQueueFilter(null, null,
                Instant.now().minus(HOUR), null, null));
        assertThat(recent.jobs()).extracting(JobQueueEntry::jobId)
                .containsExactly(queued);

        JobQueueResponse earlier = queue(new JobQueueFilter(null, null,
                Instant.now().minus(HOUR.multipliedBy(4)),
                Instant.now().minus(HOUR.multipliedBy(2)), null));
        assertThat(earlier.jobs()).extracting(JobQueueEntry::jobId)
                .containsExactly(finished, queued);
    }

    private JobQueueResponse queue(final JobQueueFilter filter) {
        return jobQueueService.getJobQueue(organization, filter);
    }

    private static JobQueueEntry entry(final JobQueueResponse response,
            final Long jobId) {
        return response.jobs().stream()
                .filter(entry -> entry.jobId().equals(jobId))
                .findFirst()
                .orElseThrow();
    }

    private Long submitGpuJob(final int units) {
        return jobService.addJob(organization,
                new AddJobRequest(ResourceType.GPU, units, 16, null, 1))
                .jobId();
    }
}
