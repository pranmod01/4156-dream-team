package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resourceallocator.model.Job;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.OrganizationRepository;
import com.resourceallocator.repository.ResourceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobDispatcherTest {

    private static final long ORG_ID = 3L;

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private JobDispatcher dispatcher;

    private long nextResourceId;

    @BeforeEach
    void setUp() {
        dispatcher = new JobDispatcher(jobRepository, resourceRepository,
                organizationRepository);
        Organization organization = new Organization("acme", "hash", 4, 2);
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
        lenient().when(organizationRepository.findByIdForUpdate(ORG_ID))
                .thenReturn(Optional.of(organization));
        nextResourceId = 100L;
    }

    @Test
    void doesNothingForUnknownOrganization() {
        when(organizationRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        dispatcher.dispatch(99L, NOW);

        verify(jobRepository, never()).assignResource(anyLong(), anyLong());
    }

    @Test
    void startsQueuedJobOnFreeMatchingResources() {
        Resource first = gpu(80);
        Resource second = gpu(80);
        Job job = queued(1L, gpuJob(2, 40));
        givenRunning();
        givenQueued(job);
        givenOnlineResources(first, second);

        dispatcher.dispatch(ORG_ID, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getStartedAt()).isEqualTo(NOW);
        assertThat(job.getLeaseEnd()).isEqualTo(NOW.plus(Duration.ofHours(5)));
        verify(jobRepository).assignResource(1L, first.getId());
        verify(jobRepository).assignResource(1L, second.getId());
    }

    @Test
    void leavesJobQueuedWhenResourcesAreTooSmall() {
        Job job = queued(1L, gpuJob(1, 100));
        givenRunning();
        givenQueued(job);
        givenOnlineResources(gpu(80));

        dispatcher.dispatch(ORG_ID, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        verify(jobRepository, never()).assignResource(anyLong(), anyLong());
    }

    @Test
    void ignoresResourcesOfTheWrongType() {
        Job job = queued(1L, gpuJob(1, 8));
        givenRunning();
        givenQueued(job);
        givenOnlineResources(cpu(64, 16));

        dispatcher.dispatch(ORG_ID, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void matchesCpuJobsOnCoreCount() {
        Resource small = cpu(64, 4);
        Resource large = cpu(64, 16);
        Job job = queued(1L, cpuJob(1, 32, 8));
        givenRunning();
        givenQueued(job);
        givenOnlineResources(small, large);

        dispatcher.dispatch(ORG_ID, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        verify(jobRepository).assignResource(1L, large.getId());
        verify(jobRepository, never()).assignResource(1L, small.getId());
    }

    @Test
    void leavesJobQueuedWhenMatchingResourcesAreBusy() {
        Resource busy = gpu(80);
        Job job = queued(1L, gpuJob(1, 40));
        givenRunning(running(9L, gpuJob(1, 40), NOW.plusSeconds(3600)));
        givenQueued(job);
        givenOnlineResources(busy);
        when(jobRepository.findResourceIdsHeldByRunningJobs(ORG_ID))
                .thenReturn(List.of(busy.getId()));

        dispatcher.dispatch(ORG_ID, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void leavesJobQueuedWhenRunningJobsReachTheUsageLimit() {
        // The organization may use 2 GPUs and already runs one.
        Job job = queued(1L, gpuJob(2, 40));
        givenRunning(running(9L, gpuJob(1, 40), NOW.plusSeconds(3600)));
        givenQueued(job);
        givenOnlineResources(gpu(80), gpu(80), gpu(80));

        dispatcher.dispatch(ORG_ID, NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        verify(jobRepository, never()).assignResource(anyLong(), anyLong());
    }

    @Test
    void finishesExpiredLeasesAndReusesTheirCapacity() {
        Job expired = running(9L, gpuJob(2, 40), NOW);
        Job job = queued(1L, gpuJob(2, 40));
        givenRunning(expired);
        givenQueued(job);
        givenOnlineResources(gpu(80), gpu(80));

        dispatcher.dispatch(ORG_ID, NOW);

        assertThat(expired.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void keepsUnexpiredLeasesRunning() {
        Job running = running(9L, gpuJob(1, 40), NOW.plusSeconds(1));
        givenRunning(running);
        givenQueued();
        givenOnlineResources();

        dispatcher.dispatch(ORG_ID, NOW);

        assertThat(running.getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void olderJobsGetResourcesFirstAndLaterJobsMayStillStart() {
        Resource onlyGpu = gpu(80);
        Resource onlyCpu = cpu(64, 8);
        Job olderGpu = queued(1L, gpuJob(1, 40));
        Job newerGpu = queued(2L, gpuJob(1, 40));
        Job newestCpu = queued(3L, cpuJob(1, 32, 4));
        givenRunning();
        givenQueued(olderGpu, newerGpu, newestCpu);
        givenOnlineResources(onlyGpu, onlyCpu);

        dispatcher.dispatch(ORG_ID, NOW);

        assertThat(olderGpu.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(newerGpu.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(newestCpu.getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void lockAndCheckResourceInUseLocksAndReportsHeldResource() {
        givenRunning();
        when(jobRepository.findResourceIdsHeldByRunningJobs(ORG_ID))
                .thenReturn(List.of(5L));

        assertThat(dispatcher.lockAndCheckResourceInUse(ORG_ID, 5L))
                .isTrue();
        assertThat(dispatcher.lockAndCheckResourceInUse(ORG_ID, 6L))
                .isFalse();
        verify(organizationRepository, times(2)).findByIdForUpdate(ORG_ID);
    }

    @Test
    void lockAndCheckResourceInUseFinishesExpiredLeasesFirst() {
        Job expired = running(1L, gpuJob(1, 40),
                Instant.now().minusSeconds(60));
        givenRunning(expired);
        when(jobRepository.findResourceIdsHeldByRunningJobs(ORG_ID))
                .thenReturn(List.of());

        assertThat(dispatcher.lockAndCheckResourceInUse(ORG_ID, 5L))
                .isFalse();
        assertThat(expired.getStatus()).isEqualTo(JobStatus.DONE);
    }

    private void givenRunning(final Job... jobs) {
        when(jobRepository
                .findByOrganizationIdAndStatusOrderBySubmittedAtAscIdAsc(
                        ORG_ID, JobStatus.RUNNING))
                .thenReturn(List.of(jobs));
    }

    private void givenQueued(final Job... jobs) {
        when(jobRepository
                .findByOrganizationIdAndStatusOrderBySubmittedAtAscIdAsc(
                        ORG_ID, JobStatus.QUEUED))
                .thenReturn(List.of(jobs));
    }

    private void givenOnlineResources(final Resource... resources) {
        when(resourceRepository.findByOrganizationIdAndOnlineTrue(ORG_ID))
                .thenReturn(List.of(resources));
    }

    private static Job gpuJob(final int units, final int memoryGb) {
        return new Job(ORG_ID, ResourceType.GPU, units, memoryGb, null, 5);
    }

    private static Job cpuJob(final int units, final int memoryGb,
            final int cores) {
        return new Job(ORG_ID, ResourceType.CPU, units, memoryGb, cores, 5);
    }

    private static Job queued(final long id, final Job job) {
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }

    private static Job running(final long id, final Job job,
            final Instant leaseEnd) {
        ReflectionTestUtils.setField(job, "id", id);
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(leaseEnd.minusSeconds(3600));
        job.setLeaseEnd(leaseEnd);
        return job;
    }

    private Resource gpu(final int memoryGb) {
        return withId(new Resource(ORG_ID, ResourceType.GPU, memoryGb, null,
                true));
    }

    private Resource cpu(final int memoryGb, final int cores) {
        return withId(new Resource(ORG_ID, ResourceType.CPU, memoryGb, cores,
                true));
    }

    private Resource withId(final Resource resource) {
        ReflectionTestUtils.setField(resource, "id", nextResourceId++);
        return resource;
    }
}
