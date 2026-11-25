package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;

import com.resourceallocator.auth.ApiKeyHasher;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class tests for {@link JobDispatcher}: runs it repeatedly at advancing
 * times against the in-memory test database, and checks that each run
 * starts from the state the previous run left. Each test runs in a
 * transaction that is rolled back afterwards.
 */
@SpringBootTest
@Transactional
class JobDispatcherClassTest {

    private static final Duration HOUR = Duration.ofHours(1);

    @Autowired
    private JobDispatcher jobDispatcher;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private JobRepository jobRepository;

    private Organization organization;

    private Instant t0;

    @BeforeEach
    void setUp() {
        organization = organization("acme", 4, 4);
        t0 = Instant.now();
    }

    @Test
    void jobsMoveThroughTheirLifecycleAcrossRuns() {
        gpu(organization, true);
        Job first = queueGpuJob(organization, 1);
        Job second = queueGpuJob(organization, 1);

        jobDispatcher.dispatch(organization.getId(), t0);
        assertThat(first.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(first.getLeaseEnd()).isEqualTo(t0.plus(HOUR));
        assertThat(second.getStatus()).isEqualTo(JobStatus.QUEUED);

        // Halfway through the lease nothing changes.
        jobDispatcher.dispatch(organization.getId(),
                t0.plus(HOUR.dividedBy(2)));
        assertThat(first.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(second.getStatus()).isEqualTo(JobStatus.QUEUED);

        // The lease ends exactly now: the first job finishes and the second
        // takes its resource in the same run.
        jobDispatcher.dispatch(organization.getId(), t0.plus(HOUR));
        assertThat(first.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(second.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(second.getStartedAt()).isEqualTo(t0.plus(HOUR));
        assertThat(jobRepository.findResourceIdsByJobId(second.getId()))
                .isEqualTo(jobRepository
                        .findResourceIdsByJobId(first.getId()));

        jobDispatcher.dispatch(organization.getId(),
                t0.plus(HOUR.multipliedBy(2)));
        assertThat(second.getStatus()).isEqualTo(JobStatus.DONE);
    }

    @Test
    void usageLimitHoldsAJobUntilRunningUnitsAreReleased() {
        Organization limited = organization("limited", 4, 2);
        gpu(limited, true);
        gpu(limited, true);
        gpu(limited, true);
        Job big = queueGpuJob(limited, 2);
        Job small = queueGpuJob(limited, 1);

        jobDispatcher.dispatch(limited.getId(), t0);
        // A GPU is free, but 2 + 1 would exceed the limit of 2.
        assertThat(big.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(small.getStatus()).isEqualTo(JobStatus.QUEUED);

        jobDispatcher.dispatch(limited.getId(), t0.plus(HOUR));
        assertThat(big.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(small.getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void smallerJobRunsAheadOfABlockedOlderJobWhichStartsLater() {
        gpu(organization, true);
        gpu(organization, true);
        Job older = queueGpuJob(organization, 3);
        Job newer = queueGpuJob(organization, 1);

        jobDispatcher.dispatch(organization.getId(), t0);
        assertThat(older.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(newer.getStatus()).isEqualTo(JobStatus.RUNNING);

        // A third GPU arrives, but the newer job still holds one.
        gpu(organization, true);
        jobDispatcher.dispatch(organization.getId(),
                t0.plus(HOUR.dividedBy(2)));
        assertThat(older.getStatus()).isEqualTo(JobStatus.QUEUED);

        jobDispatcher.dispatch(organization.getId(), t0.plus(HOUR));
        assertThat(newer.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(older.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(jobRepository.findResourceIdsByJobId(older.getId()))
                .hasSize(3);
    }

    @Test
    void offlineResourceIsUsedOnlyAfterItComesBackOnline() {
        Resource resource = gpu(organization, false);
        Job job = queueGpuJob(organization, 1);

        jobDispatcher.dispatch(organization.getId(), t0);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);

        resource.setOnline(true);
        resourceRepository.flush();
        jobDispatcher.dispatch(organization.getId(), t0);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void resourceCheckFinishesExpiredLeasesFirst() {
        Resource resource = gpu(organization, true);
        Job expired = queueGpuJob(organization, 1);
        // Started two hours ago, so its one-hour lease has already ended,
        // but no run has marked it done yet.
        jobDispatcher.dispatch(organization.getId(),
                Instant.now().minus(HOUR.multipliedBy(2)));
        assertThat(expired.getStatus()).isEqualTo(JobStatus.RUNNING);

        assertThat(jobDispatcher.lockAndCheckResourceInUse(
                organization.getId(), resource.getId())).isFalse();
        assertThat(expired.getStatus()).isEqualTo(JobStatus.DONE);

        Job current = queueGpuJob(organization, 1);
        jobDispatcher.dispatch(organization.getId(), Instant.now());
        assertThat(current.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(jobDispatcher.lockAndCheckResourceInUse(
                organization.getId(), resource.getId())).isTrue();
    }

    @Test
    void runsForOneOrganizationLeaveOthersAlone() {
        Organization other = organization("other", 4, 4);
        gpu(organization, true);
        gpu(other, true);
        Job mine = queueGpuJob(organization, 1);
        Job theirs = queueGpuJob(other, 1);

        jobDispatcher.dispatch(organization.getId(), t0);

        assertThat(mine.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(theirs.getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    private Organization organization(final String name, final int maxCpus,
            final int maxGpus) {
        return organizationRepository.saveAndFlush(new Organization(
                name, ApiKeyHasher.hash(name), maxCpus, maxGpus));
    }

    private Resource gpu(final Organization owner, final boolean online) {
        return resourceRepository.saveAndFlush(new Resource(owner.getId(),
                ResourceType.GPU, 40, null, online));
    }

    private Job queueGpuJob(final Organization owner, final int units) {
        return jobRepository.saveAndFlush(new Job(owner.getId(),
                ResourceType.GPU, units, 16, null, 1));
    }
}
