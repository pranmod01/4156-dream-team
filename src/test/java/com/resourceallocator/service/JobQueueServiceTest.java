package com.resourceallocator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.resourceallocator.dto.BlockedReason;
import com.resourceallocator.dto.JobQueueEntry;
import com.resourceallocator.dto.JobQueueFilter;
import com.resourceallocator.dto.JobQueueResponse;
import com.resourceallocator.dto.JobStatusCounts;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.model.Job;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.ResourceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobQueueServiceTest {

    private static final long ORG_ID = 3L;

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    private static final JobQueueFilter NO_FILTER =
            new JobQueueFilter(null, null, null, null, null);

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ResourceRepository resourceRepository;

    private JobQueueService service;

    private Organization organization;

    @BeforeEach
    void setUp() {
        service = new JobQueueService(jobRepository, resourceRepository);
        organization = new Organization("acme", "hash", 4, 2);
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
    }

    @Test
    void assignsQueuePositionsToQueuedJobsInSubmissionOrder() {
        Job running = running(1L, gpuJob(1), 0, 10);
        Job first = queued(2L, gpuJob(1), 1);
        Job second = queued(3L, gpuJob(1), 2);
        givenJobs(running, first, second);
        givenOnlineResources(gpu(80), gpu(80));

        JobQueueResponse response = service.getJobQueue(organization,
                NO_FILTER);

        assertThat(response.jobs()).extracting(JobQueueEntry::jobId)
                .containsExactly(1L, 2L, 3L);
        assertThat(response.jobs()).extracting(JobQueueEntry::queuePosition)
                .containsExactly(null, 1, 2);
    }

    @Test
    void resourceTypeFilterKeepsPositionsFromTheFullQueue() {
        Job first = queued(1L, gpuJob(1), 1);
        Job second = queued(2L, cpuJob(1, 4), 2);
        givenJobs(first, second);
        givenOnlineResources(gpu(80), cpu(64, 8));

        JobQueueResponse response = service.getJobQueue(organization,
                new JobQueueFilter(null, ResourceType.CPU, null, null, null));

        assertThat(response.jobs()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.jobId()).isEqualTo(2L);
                    assertThat(entry.queuePosition()).isEqualTo(2);
                });
    }

    @Test
    void flagsJobWhenTooFewOnlineResourcesMatch() {
        Job job = queued(1L, new Job(ORG_ID, ResourceType.GPU, 2, 40, null,
                1), 1);
        givenJobs(job);
        givenOnlineResources(gpu(80), gpu(16));

        JobQueueEntry entry = service.getJobQueue(organization, NO_FILTER)
                .jobs().get(0);

        assertThat(entry.blocked()).isTrue();
        assertThat(entry.blockedReason())
                .isEqualTo(BlockedReason.NO_MATCHING_RESOURCE);
    }

    @Test
    void cpuWithTooFewCoresDoesNotMatch() {
        Job job = queued(1L, cpuJob(1, 16), 1);
        givenJobs(job);
        givenOnlineResources(cpu(64, 8));

        JobQueueEntry entry = service.getJobQueue(organization, NO_FILTER)
                .jobs().get(0);

        assertThat(entry.blockedReason())
                .isEqualTo(BlockedReason.NO_MATCHING_RESOURCE);
    }

    @Test
    void flagsJobThatWouldExceedUsageLimit() {
        Job runningJob = running(1L, gpuJob(2), 0, 10);
        Job waiting = queued(2L, gpuJob(1), 1);
        givenJobs(runningJob, waiting);
        givenOnlineResources(gpu(80), gpu(80), gpu(80));

        JobQueueEntry entry = service.getJobQueue(organization, NO_FILTER)
                .jobs().get(1);

        assertThat(entry.blocked()).isTrue();
        assertThat(entry.blockedReason())
                .isEqualTo(BlockedReason.EXCEEDS_USAGE_LIMIT);
    }

    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    @Test
    void flagsJobWhenMatchingResourcesAreHeldByRunningJobs() {
        Job runningJob = running(1L, gpuJob(1), 0, 10);
        Job waiting = queued(2L, gpuJob(1), 1);
        givenJobs(runningJob, waiting);
        givenOnlineResources(withId(gpu(80), 10L));
        givenBusyResources(10L);

        JobQueueEntry entry = service.getJobQueue(organization, NO_FILTER)
                .jobs().get(1);

        assertThat(entry.blocked()).isTrue();
        assertThat(entry.blockedReason())
                .isEqualTo(BlockedReason.RESOURCES_IN_USE);
    }

    @Test
    void freeMatchingResourceLetsJobRunDespiteBusyOnes() {
        Job runningJob = running(1L, gpuJob(1), 0, 10);
        Job waiting = queued(2L, gpuJob(1), 1);
        givenJobs(runningJob, waiting);
        givenOnlineResources(withId(gpu(80), 10L), withId(gpu(80), 11L));
        givenBusyResources(10L);

        JobQueueEntry entry = service.getJobQueue(organization, NO_FILTER)
                .jobs().get(1);

        assertThat(entry.blocked()).isFalse();
    }

    @Test
    void usageLimitIsReportedBeforeBusyResources() {
        Job runningJob = running(1L, gpuJob(2), 0, 10);
        Job waiting = queued(2L, gpuJob(1), 1);
        givenJobs(runningJob, waiting);
        givenOnlineResources(withId(gpu(80), 10L), withId(gpu(80), 11L));
        givenBusyResources(10L, 11L);

        JobQueueEntry entry = service.getJobQueue(organization, NO_FILTER)
                .jobs().get(1);

        assertThat(entry.blockedReason())
                .isEqualTo(BlockedReason.EXCEEDS_USAGE_LIMIT);
    }
    // END AI-generated

    @Test
    void schedulableJobIsNotBlocked() {
        Job job = queued(1L, cpuJob(2, 4), 1);
        givenJobs(job);
        givenOnlineResources(cpu(64, 8), cpu(32, 4));

        JobQueueEntry entry = service.getJobQueue(organization, NO_FILTER)
                .jobs().get(0);

        assertThat(entry.blocked()).isFalse();
        assertThat(entry.blockedReason()).isNull();
    }

    @Test
    void timeWindowFiltersStartedJobsButKeepsQueuedOnes() {
        Job beforeWindow = done(1L, gpuJob(1), 0, 1);
        Job insideWindow = running(2L, gpuJob(1), 5, 10);
        Job waiting = queued(3L, gpuJob(1), 6);
        givenJobs(beforeWindow, insideWindow, waiting);
        givenOnlineResources(gpu(80));

        JobQueueResponse response = service.getJobQueue(organization,
                new JobQueueFilter(null, null, T0.plus(hours(4)),
                        T0.plus(hours(8)), null));

        assertThat(response.jobs()).extracting(JobQueueEntry::jobId)
                .containsExactly(2L, 3L);
    }

    @Test
    void limitCapsNumberOfResults() {
        givenJobs(queued(1L, gpuJob(1), 1), queued(2L, gpuJob(1), 2),
                queued(3L, gpuJob(1), 3));
        givenOnlineResources(gpu(80));

        JobQueueResponse response = service.getJobQueue(organization,
                new JobQueueFilter(null, null, null, null, 2));

        assertThat(response.jobs()).extracting(JobQueueEntry::jobId)
                .containsExactly(1L, 2L);
    }

    @Test
    void summaryCountsAllJobsRegardlessOfFilters() {
        givenJobs(done(1L, gpuJob(1), 0, 1), running(2L, gpuJob(1), 2, 10),
                queued(3L, gpuJob(1), 3), queued(4L, gpuJob(1), 4));
        givenOnlineResources(gpu(80));

        JobQueueResponse response = service.getJobQueue(organization,
                new JobQueueFilter(JobStatus.DONE, null, null, null, null));

        assertThat(response.jobs()).hasSize(1);
        assertThat(response.summary())
                .isEqualTo(new JobStatusCounts(2, 1, 1));
    }

    @Test
    void rejectsWindowEndingBeforeItStarts() {
        JobQueueFilter filter = new JobQueueFilter(null, null,
                T0.plus(hours(2)), T0, null);

        assertThatThrownBy(() -> service.getJobQueue(organization, filter))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("end");
    }

    @Test
    void rejectsNonPositiveLimit() {
        JobQueueFilter filter = new JobQueueFilter(null, null, null, null, 0);

        assertThatThrownBy(() -> service.getJobQueue(organization, filter))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("limit");
    }

    private void givenJobs(final Job... jobs) {
        when(jobRepository.findByOrganizationIdOrderBySubmittedAtAscIdAsc(
                ORG_ID)).thenReturn(List.of(jobs));
    }

    private void givenOnlineResources(final Resource... resources) {
        when(resourceRepository.findByOrganizationIdAndOnlineTrue(ORG_ID))
                .thenReturn(List.of(resources));
    }

    // AI-generated (Claude Code, Claude Opus 5.5)
    private void givenBusyResources(final Long... resourceIds) {
        when(resourceRepository.findResourceIdsHeldByRunningJobs(ORG_ID))
                .thenReturn(List.of(resourceIds));
    }

    // AI-generated (Claude Code, Claude Opus 5.5)
    private static Resource withId(final Resource resource, final long id) {
        ReflectionTestUtils.setField(resource, "id", id);
        return resource;
    }

    private static Job gpuJob(final int units) {
        return new Job(ORG_ID, ResourceType.GPU, units, 40, null, 4);
    }

    private static Job cpuJob(final int units, final int minCores) {
        return new Job(ORG_ID, ResourceType.CPU, units, 16, minCores, 4);
    }

    private static Resource gpu(final int memoryGb) {
        return new Resource(ORG_ID, ResourceType.GPU, memoryGb, null, true);
    }

    private static Resource cpu(final int memoryGb, final int cores) {
        return new Resource(ORG_ID, ResourceType.CPU, memoryGb, cores, true);
    }

    private static Job queued(final long id, final Job job,
            final int submittedHour) {
        ReflectionTestUtils.setField(job, "id", id);
        ReflectionTestUtils.setField(job, "submittedAt",
                T0.plus(hours(submittedHour)));
        return job;
    }

    private static Job running(final long id, final Job job,
            final int startHour, final int leaseEndHour) {
        queued(id, job, startHour);
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(T0.plus(hours(startHour)));
        job.setLeaseEnd(T0.plus(hours(leaseEndHour)));
        return job;
    }

    private static Job done(final long id, final Job job,
            final int startHour, final int leaseEndHour) {
        running(id, job, startHour, leaseEndHour);
        job.setStatus(JobStatus.DONE);
        return job;
    }

    private static Duration hours(final int hours) {
        return Duration.ofHours(hours);
    }
}
