package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.dto.AddJobRequest;
import com.resourceallocator.dto.JobResponse;
import com.resourceallocator.dto.UpdateClientLimitsRequest;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.exception.NotFoundException;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.OrganizationRepository;
import com.resourceallocator.repository.ResourceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class tests for {@link JobService}: walks jobs through submit, look up,
 * and delete in sequence, with the real dispatcher and the in-memory test
 * database. Each test runs in a transaction that is rolled back
 * afterwards.
 */
@SpringBootTest
@Transactional
class JobServiceClassTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private JobDispatcher jobDispatcher;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private JobRepository jobRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = organizationRepository.saveAndFlush(new Organization(
                "acme", ApiKeyHasher.hash("key"), 4, 2));
    }

    @Test
    void queuedJobCanBeLookedUpThenDeleted() {
        // No resources, so the job stays queued.
        JobResponse added = jobService.addJob(organization, gpuJob(1));
        assertThat(added.status()).isEqualTo(JobStatus.QUEUED);

        assertThat(jobService.getJobStatus(organization, added.jobId())
                .status()).isEqualTo(JobStatus.QUEUED);
        jobService.deleteJob(organization, added.jobId());

        assertThatThrownBy(() ->
                jobService.getJobStatus(organization, added.jobId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void runningJobCanBeDeletedOnlyAfterItsLeaseEnds() {
        addGpus(1);
        JobResponse added = jobService.addJob(organization, gpuJob(1));
        assertThat(added.status()).isEqualTo(JobStatus.RUNNING);
        assertThat(added.resourceIds()).hasSize(1);

        assertThatThrownBy(() ->
                jobService.deleteJob(organization, added.jobId()))
                .isInstanceOf(ConflictException.class);

        jobDispatcher.dispatch(organization.getId(),
                Instant.now().plus(Duration.ofHours(2)));
        assertThat(jobService.getJobStatus(organization, added.jobId())
                .status()).isEqualTo(JobStatus.DONE);
        jobService.deleteJob(organization, added.jobId());
        assertThat(jobRepository.findResourceIdsByJobId(added.jobId()))
                .isEmpty();
    }

    @Test
    void laterJobStartsOnTheResourceAnEarlierJobFreed() {
        addGpus(1);
        JobResponse first = jobService.addJob(organization, gpuJob(1));
        JobResponse second = jobService.addJob(organization, gpuJob(1));
        assertThat(second.status()).isEqualTo(JobStatus.QUEUED);

        jobDispatcher.dispatch(organization.getId(),
                Instant.now().plus(Duration.ofHours(2)));

        JobResponse started =
                jobService.getJobStatus(organization, second.jobId());
        assertThat(started.status()).isEqualTo(JobStatus.RUNNING);
        assertThat(started.resourceIds()).isEqualTo(first.resourceIds());
    }

    @Test
    void submissionUsesTheStoredLimitNotTheCallersCopy() {
        clientService.updateLimits(organization,
                new UpdateClientLimitsRequest(null, 1));
        entityManager.flush();
        entityManager.clear();
        // A copy loaded before the update still says maxGpus = 2.
        Organization stale = new Organization("acme", "hash", 4, 2);
        ReflectionTestUtils.setField(stale, "id", organization.getId());

        assertThatThrownBy(() -> jobService.addJob(stale, gpuJob(2)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("limit of 1");
        assertThat(jobRepository
                .findByOrganizationIdOrderBySubmittedAtAscIdAsc(
                        organization.getId())).isEmpty();
    }

    private void addGpus(final int count) {
        for (int i = 0; i < count; i++) {
            resourceRepository.save(new Resource(organization.getId(),
                    ResourceType.GPU, 40, null, true));
        }
        resourceRepository.flush();
    }

    private static AddJobRequest gpuJob(final int units) {
        return new AddJobRequest(ResourceType.GPU, units, 16, null, 1);
    }
}
