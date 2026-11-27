package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.dto.AddJobRequest;
import com.resourceallocator.dto.AddResourceRequest;
import com.resourceallocator.dto.JobResponse;
import com.resourceallocator.dto.ResourceFilter;
import com.resourceallocator.dto.ResourceListResponse;
import com.resourceallocator.dto.ResourceStatus;
import com.resourceallocator.dto.ResourceStatusEntry;
import com.resourceallocator.dto.UpdateResourceRequest;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.exception.NotFoundException;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.OrganizationRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class tests for {@link ResourceService}: takes resources through add,
 * list, update and delete in sequence, while jobs use them, with the real
 * dispatcher and the in-memory test database. Each test runs in a
 * transaction that is rolled back afterwards.
 */
@SpringBootTest
@Transactional
class ResourceServiceClassTest {

    private static final ResourceFilter ALL =
            new ResourceFilter(null, null, null, null);

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobDispatcher jobDispatcher;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JobRepository jobRepository;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = organizationRepository.saveAndFlush(new Organization(
                "acme", ApiKeyHasher.hash("key"), 4, 2));
    }

    @Test
    void resourceStatusFollowsTheJobUsingIt() {
        Long gpuId = addGpu(true);
        ResourceListResponse idle = resourceService.listResources(
                organization, ALL);
        assertThat(only(idle).status()).isEqualTo(ResourceStatus.AVAILABLE);
        assertThat(idle.summary().gpu().freeResources()).isEqualTo(1);

        JobResponse job = jobService.addJob(organization, gpuJob());
        ResourceListResponse busy = resourceService.listResources(
                organization, ALL);
        assertThat(only(busy).status()).isEqualTo(ResourceStatus.IN_USE);
        assertThat(only(busy).runningJobId()).isEqualTo(job.jobId());
        assertThat(busy.summary().gpu().inUse()).isEqualTo(1);
        assertThat(busy.summary().gpu().remaining()).isEqualTo(1);
        assertThat(busy.summary().gpu().freeResources()).isZero();

        // While the job runs, the resource can be neither taken offline
        // nor deleted.
        assertThatThrownBy(() -> resourceService.updateResource(
                organization, gpuId, new UpdateResourceRequest(false)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() ->
                resourceService.deleteResource(organization, gpuId))
                .isInstanceOf(ConflictException.class);

        endLeases();
        resourceService.updateResource(organization, gpuId,
                new UpdateResourceRequest(false));
        assertThat(only(resourceService.listResources(organization, ALL))
                .status()).isEqualTo(ResourceStatus.OFFLINE);

        resourceService.updateResource(organization, gpuId,
                new UpdateResourceRequest(true));
        assertThat(only(resourceService.listResources(organization, ALL))
                .status()).isEqualTo(ResourceStatus.AVAILABLE);

        resourceService.deleteResource(organization, gpuId);
        assertThat(resourceService.listResources(organization, ALL)
                .resources()).isEmpty();
        assertThatThrownBy(() -> resourceService.listResources(organization,
                new ResourceFilter(gpuId, null, null, null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() ->
                resourceService.deleteResource(organization, gpuId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void reenabledResourceIsPickedUpByTheNextListing() {
        Long gpuId = addGpu(false);
        JobResponse job = jobService.addJob(organization, gpuJob());
        assertThat(job.status()).isEqualTo(JobStatus.QUEUED);

        // Bringing the resource online does not start the job by itself.
        resourceService.updateResource(organization, gpuId,
                new UpdateResourceRequest(true));
        assertThat(jobRepository.findById(job.jobId()).orElseThrow()
                .getStatus()).isEqualTo(JobStatus.QUEUED);

        // Listing runs the dispatcher, which starts it.
        ResourceListResponse listed = resourceService.listResources(
                organization, ALL);
        assertThat(only(listed).status()).isEqualTo(ResourceStatus.IN_USE);
        assertThat(only(listed).runningJobId()).isEqualTo(job.jobId());
    }

    @Test
    void deletingAResourceAFinishedJobUsedKeepsTheJob() {
        Long gpuId = addGpu(true);
        JobResponse job = jobService.addJob(organization, gpuJob());
        assertThat(job.resourceIds()).containsExactly(gpuId);
        endLeases();

        resourceService.deleteResource(organization, gpuId);

        JobResponse after = jobService.getJobStatus(organization, job.jobId());
        assertThat(after.status()).isEqualTo(JobStatus.DONE);
        assertThat(after.resourceIds()).isEmpty();
    }

    @Test
    void filtersNarrowTheListButNotTheSummary() {
        Long bigCpu = resourceService.addResource(organization,
                new AddResourceRequest(ResourceType.CPU, 64, 16, true)).id();
        resourceService.addResource(organization,
                new AddResourceRequest(ResourceType.CPU, 8, 2, true));
        addGpu(true);

        ResourceListResponse filtered = resourceService.listResources(
                organization, new ResourceFilter(null, null, null, 8));

        assertThat(filtered.resources()).extracting(ResourceStatusEntry::id)
                .containsExactly(bigCpu);
        assertThat(filtered.summary().cpu().freeResources()).isEqualTo(2);
        assertThat(filtered.summary().gpu().freeResources()).isEqualTo(1);
    }

    private Long addGpu(final boolean online) {
        return resourceService.addResource(organization,
                new AddResourceRequest(ResourceType.GPU, 40, null, online))
                .id();
    }

    private void endLeases() {
        jobDispatcher.dispatch(organization.getId(),
                Instant.now().plus(Duration.ofHours(2)));
    }

    private static ResourceStatusEntry only(
            final ResourceListResponse response) {
        assertThat(response.resources()).hasSize(1);
        return response.resources().get(0);
    }

    private static AddJobRequest gpuJob() {
        return new AddJobRequest(ResourceType.GPU, 1, 16, null, 1);
    }
}
