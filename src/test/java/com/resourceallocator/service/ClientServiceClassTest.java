package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.dto.AddJobRequest;
import com.resourceallocator.dto.ClientResponse;
import com.resourceallocator.dto.CreateClientRequest;
import com.resourceallocator.dto.CreateClientResponse;
import com.resourceallocator.dto.JobResponse;
import com.resourceallocator.dto.UpdateClientLimitsRequest;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.model.Job;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class tests for {@link ClientService}: calls its methods in sequence
 * with its real collaborators and the in-memory test database, and checks
 * that state carries over correctly from one call to the next. Each test
 * runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@Transactional
class ClientServiceClassTest {

    @Autowired
    private ClientService clientService;

    @Autowired
    private JobService jobService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private JobRepository jobRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void updatedLimitsBelongToTheRegisteredClient() {
        CreateClientResponse created = clientService.createClient(
                new CreateClientRequest("acme", 4, 2));
        Organization organization = load(created.id());

        ClientResponse updated = clientService.updateLimits(organization,
                new UpdateClientLimitsRequest(8, 3));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.orgName()).isEqualTo("acme");
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        // The API key issued at registration still finds the client, now
        // with the new limits.
        Organization byKey = organizationRepository
                .findByApiKeyHash(ApiKeyHasher.hash(created.apiKey()))
                .orElseThrow();
        assertThat(byKey.getMaxCpus()).isEqualTo(8);
        assertThat(byKey.getMaxGpus()).isEqualTo(3);
    }

    @Test
    void successiveUpdatesEachKeepTheOmittedLimit() {
        Organization organization = register(4, 2);

        clientService.updateLimits(organization,
                new UpdateClientLimitsRequest(8, null));
        ClientResponse second = clientService.updateLimits(organization,
                new UpdateClientLimitsRequest(null, 5));

        assertThat(second.maxCpus()).isEqualTo(8);
        assertThat(second.maxGpus()).isEqualTo(5);
    }

    @Test
    void omittedLimitComesFromTheDatabaseNotTheCallersCopy() {
        Organization organization = register(4, 2);
        clientService.updateLimits(organization,
                new UpdateClientLimitsRequest(null, 6));
        entityManager.flush();
        entityManager.clear();
        // A copy loaded before the update still says maxGpus = 2.
        Organization stale = new Organization("acme", "hash", 4, 2);
        ReflectionTestUtils.setField(stale, "id", organization.getId());

        ClientResponse response = clientService.updateLimits(stale,
                new UpdateClientLimitsRequest(1, null));

        assertThat(response.maxGpus()).isEqualTo(6);
    }

    @Test
    void rejectedUpdateLeavesLimitsUnchangedAndLaterUpdatesWork() {
        Organization organization = register(4, 2);
        queue(organization, ResourceType.GPU, 2);

        assertThatThrownBy(() -> clientService.updateLimits(organization,
                new UpdateClientLimitsRequest(1, 1)))
                .isInstanceOf(ConflictException.class);
        Organization afterReject = reload(organization);
        assertThat(afterReject.getMaxCpus()).isEqualTo(4);
        assertThat(afterReject.getMaxGpus()).isEqualTo(2);

        // A limit equal to the queued job's request is allowed.
        ClientResponse accepted = clientService.updateLimits(afterReject,
                new UpdateClientLimitsRequest(1, 2));
        assertThat(accepted.maxCpus()).isEqualTo(1);
        assertThat(accepted.maxGpus()).isEqualTo(2);
    }

    @Test
    void onlyQueuedJobsOfThisClientAndTypeBlockLowering() {
        Organization organization = register(4, 4);
        Organization other = register("other", 4, 4);
        Job running = queue(organization, ResourceType.GPU, 3);
        running.setStatus(JobStatus.RUNNING);
        running.setStartedAt(Instant.now());
        running.setLeaseEnd(Instant.now().plus(Duration.ofHours(1)));
        queue(organization, ResourceType.GPU, 4).setStatus(JobStatus.DONE);
        queue(organization, ResourceType.CPU, 4);
        queue(other, ResourceType.GPU, 4);
        entityManager.flush();

        ClientResponse response = clientService.updateLimits(organization,
                new UpdateClientLimitsRequest(null, 1));

        assertThat(response.maxGpus()).isEqualTo(1);
        // The running job is not stopped by the lower limit.
        assertThat(jobRepository.findById(running.getId()).orElseThrow()
                .getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void lowerLimitHoldsNewJobsUntilRunningJobsFinish() {
        Organization organization = register(4, 2);
        addGpus(organization, 2);
        JobResponse first = jobService.addJob(organization, gpuJob(2));
        assertThat(first.status()).isEqualTo(JobStatus.RUNNING);

        clientService.updateLimits(organization,
                new UpdateClientLimitsRequest(null, 1));
        JobResponse second = jobService.addJob(organization, gpuJob(1));

        assertThat(second.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(jobService.getJobStatus(organization, first.jobId())
                .status()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void raisingLimitStartsWaitingJobsInTheSameCall() {
        Organization organization = register(4, 2);
        addGpus(organization, 3);
        jobService.addJob(organization, gpuJob(2));
        JobResponse waiting = jobService.addJob(organization, gpuJob(1));
        assertThat(waiting.status()).isEqualTo(JobStatus.QUEUED);

        clientService.updateLimits(organization,
                new UpdateClientLimitsRequest(null, 3));

        entityManager.flush();
        entityManager.clear();
        assertThat(jobRepository.findById(waiting.jobId()).orElseThrow()
                .getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    private Organization register(final int maxCpus, final int maxGpus) {
        return register("acme", maxCpus, maxGpus);
    }

    private Organization register(final String name, final int maxCpus,
            final int maxGpus) {
        return load(clientService.createClient(
                new CreateClientRequest(name, maxCpus, maxGpus)).id());
    }

    private Organization load(final Long id) {
        return organizationRepository.findById(id).orElseThrow();
    }

    private Organization reload(final Organization organization) {
        entityManager.clear();
        return load(organization.getId());
    }

    private Job queue(final Organization organization,
            final ResourceType type, final int units) {
        return jobRepository.saveAndFlush(new Job(organization.getId(), type,
                units, 16, type == ResourceType.CPU ? 2 : null, 1));
    }

    private void addGpus(final Organization organization, final int count) {
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
