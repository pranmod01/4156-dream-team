package com.resourceallocator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resourceallocator.dto.AddResourceRequest;
import com.resourceallocator.dto.ResourceFilter;
import com.resourceallocator.dto.ResourceListResponse;
import com.resourceallocator.dto.ResourceResponse;
import com.resourceallocator.dto.ResourceStatus;
import com.resourceallocator.dto.ResourceStatusEntry;
import com.resourceallocator.dto.ResourceSummary;
import com.resourceallocator.dto.ResourceUsage;
import com.resourceallocator.dto.UpdateResourceRequest;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.exception.NotFoundException;
import com.resourceallocator.model.Job;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.ResourceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    private static final long ORG_ID = 7L;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private JobDispatcher jobDispatcher;

    @Mock
    private JobRepository jobRepository;

    private ResourceService resourceService;

    private Organization organization;

    @BeforeEach
    void setUp() {
        resourceService = new ResourceService(resourceRepository,
                jobDispatcher, jobRepository);
        organization = new Organization("acme", "hash", 4, 2);
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
    }

    @Test
    void addResourceSavesCpuOwnedByOrganization() {
        when(resourceRepository.save(any(Resource.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0),
                        11L));

        ResourceResponse response = resourceService.addResource(
                organization,
                new AddResourceRequest(ResourceType.CPU, 32, 8, true));

        ArgumentCaptor<Resource> saved =
                ArgumentCaptor.forClass(Resource.class);
        verify(resourceRepository).save(saved.capture());
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(response).isEqualTo(new ResourceResponse(
                11L, ResourceType.CPU, 32, 8, true));
    }

    @Test
    void addResourceSavesGpuWithoutCores() {
        when(resourceRepository.save(any(Resource.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0),
                        12L));

        ResourceResponse response = resourceService.addResource(
                organization,
                new AddResourceRequest(ResourceType.GPU, 80, null, false));

        assertThat(response).isEqualTo(new ResourceResponse(
                12L, ResourceType.GPU, 80, null, false));
    }

    @Test
    void addResourceRejectsCpuWithoutCores() {
        AddResourceRequest request =
                new AddResourceRequest(ResourceType.CPU, 32, null, true);

        assertThatThrownBy(() ->
                resourceService.addResource(organization, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cores");
        verify(resourceRepository, never()).save(any());
    }

    @Test
    void addResourceRejectsGpuWithCores() {
        AddResourceRequest request =
                new AddResourceRequest(ResourceType.GPU, 80, 4, true);

        assertThatThrownBy(() ->
                resourceService.addResource(organization, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cores");
        verify(resourceRepository, never()).save(any());
    }

    @Test
    void updateResourceChangesOnlineState() {
        Resource resource = withId(
                new Resource(ORG_ID, ResourceType.GPU, 80, null, true), 5L);
        when(resourceRepository.findByIdAndOrganizationId(5L, ORG_ID))
                .thenReturn(Optional.of(resource));

        ResourceResponse response = resourceService.updateResource(
                organization, 5L, new UpdateResourceRequest(false));

        assertThat(resource.isOnline()).isFalse();
        assertThat(response.online()).isFalse();
        assertThat(response.id()).isEqualTo(5L);
    }

    @Test
    void updateResourceRejectsUnknownOrForeignResource() {
        when(resourceRepository.findByIdAndOrganizationId(99L, ORG_ID))
                .thenReturn(Optional.empty());
        UpdateResourceRequest request = new UpdateResourceRequest(false);

        assertThatThrownBy(() -> resourceService.updateResource(
                organization, 99L, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateResourceRejectsResourceInUse() {
        Resource resource = withId(
                new Resource(ORG_ID, ResourceType.GPU, 80, null, true), 5L);
        when(resourceRepository.findByIdAndOrganizationId(5L, ORG_ID))
                .thenReturn(Optional.of(resource));
        when(jobDispatcher.lockAndCheckResourceInUse(ORG_ID, 5L))
                .thenReturn(true);

        assertThatThrownBy(() ->
                resourceService.updateResource(
                        organization, 5L, new UpdateResourceRequest(false)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("in use");
        assertThat(resource.isOnline()).isTrue();
    }

    @Test
    void updateResourceDoesNotCallJobDispatcherWhenSettingOnline() {
        Resource resource = withId(
                new Resource(ORG_ID, ResourceType.GPU, 80, null, true), 5L);
        when(resourceRepository.findByIdAndOrganizationId(5L, ORG_ID))
                .thenReturn(Optional.of(resource));

        ResourceResponse response = resourceService.updateResource(
                organization, 5L, new UpdateResourceRequest(true));

        assertThat(response.online()).isTrue();
        verify(jobDispatcher, never()).lockAndCheckResourceInUse(ORG_ID, 5L);
    }

    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    @Test
    void listResourcesReportsStatusOfEachResource() {
        givenResources(cpu(1L, 32, 8, true), gpu(2L, 80, true),
                gpu(3L, 80, false));
        givenRunning(runningJob(ResourceType.GPU, 1), new Object[] {2L, 9L});

        ResourceListResponse response = resourceService.listResources(
                organization, noFilter());

        assertThat(response.resources()).containsExactly(
                new ResourceStatusEntry(1L, ResourceType.CPU, 32, 8, true,
                        ResourceStatus.AVAILABLE, null),
                new ResourceStatusEntry(2L, ResourceType.GPU, 80, null, true,
                        ResourceStatus.IN_USE, 9L),
                new ResourceStatusEntry(3L, ResourceType.GPU, 80, null,
                        false, ResourceStatus.OFFLINE, null));
    }

    @Test
    void listResourcesRunsDispatcherFirst() {
        givenResources();
        givenRunning();

        resourceService.listResources(organization, noFilter());

        InOrder inOrder = inOrder(jobDispatcher, resourceRepository);
        inOrder.verify(jobDispatcher).dispatch(ORG_ID);
        inOrder.verify(resourceRepository)
                .findByOrganizationIdOrderByIdAsc(ORG_ID);
    }

    @Test
    void listResourcesFiltersById() {
        givenResources(cpu(1L, 32, 8, true), gpu(2L, 80, true));
        givenRunning();

        ResourceListResponse response = resourceService.listResources(
                organization, new ResourceFilter(2L, null, null, null));

        assertThat(response.resources()).extracting(ResourceStatusEntry::id)
                .containsExactly(2L);
    }

    @Test
    void listResourcesRejectsUnknownOrForeignId() {
        givenResources(cpu(1L, 32, 8, true));
        ResourceFilter filter = new ResourceFilter(99L, null, null, null);

        assertThatThrownBy(() ->
                resourceService.listResources(organization, filter))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listResourcesFiltersByTypeAndMemory() {
        givenResources(cpu(1L, 32, 8, true), gpu(2L, 40, true),
                gpu(3L, 80, true));
        givenRunning();

        ResourceListResponse response = resourceService.listResources(
                organization,
                new ResourceFilter(null, ResourceType.GPU, 64, null));

        assertThat(response.resources()).extracting(ResourceStatusEntry::id)
                .containsExactly(3L);
    }

    @Test
    void listResourcesCoreFilterKeepsOnlyLargeEnoughCpus() {
        givenResources(cpu(1L, 32, 4, true), cpu(2L, 32, 16, true),
                gpu(3L, 80, true));
        givenRunning();

        ResourceListResponse response = resourceService.listResources(
                organization, new ResourceFilter(null, null, null, 8));

        assertThat(response.resources()).extracting(ResourceStatusEntry::id)
                .containsExactly(2L);
    }

    @Test
    void listResourcesRejectsNonPositiveMinimums() {
        ResourceFilter memory = new ResourceFilter(null, null, 0, null);
        ResourceFilter cores = new ResourceFilter(null, null, null, -1);

        assertThatThrownBy(() ->
                resourceService.listResources(organization, memory))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("minMemoryGb");
        assertThatThrownBy(() ->
                resourceService.listResources(organization, cores))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("minCores");
        verify(jobDispatcher, never()).dispatch(ORG_ID);
    }

    @Test
    void listResourcesSummarizesUsageAgainstLimits() {
        // Limits are 4 CPUs and 2 GPUs. Two GPUs are free, but a running
        // 2-unit GPU job leaves no GPU allowance.
        givenResources(cpu(1L, 32, 8, true), cpu(2L, 32, 8, false),
                gpu(3L, 80, true), gpu(4L, 80, true), gpu(5L, 80, true),
                gpu(6L, 80, true));
        givenRunning(runningJob(ResourceType.GPU, 2),
                new Object[] {3L, 9L}, new Object[] {4L, 9L});

        ResourceListResponse response = resourceService.listResources(
                organization, noFilter());

        assertThat(response.summary()).isEqualTo(new ResourceSummary(
                new ResourceUsage(4, 0, 4, 1),
                new ResourceUsage(2, 2, 0, 2)));
    }

    @Test
    void listResourcesSummaryIgnoresFilters() {
        givenResources(cpu(1L, 32, 8, true), gpu(2L, 80, true));
        givenRunning();

        ResourceListResponse response = resourceService.listResources(
                organization,
                new ResourceFilter(null, ResourceType.CPU, null, null));

        assertThat(response.summary().gpu().freeResources()).isEqualTo(1);
    }

    private void givenResources(final Resource... resources) {
        when(resourceRepository.findByOrganizationIdOrderByIdAsc(ORG_ID))
                .thenReturn(List.of(resources));
    }

    // Stubs the running jobs (empty unless given) and the
    // [resourceId, jobId] pairs they hold.
    private void givenRunning(final Job job, final Object[]... assignments) {
        when(jobRepository
                .findByOrganizationIdAndStatusOrderBySubmittedAtAscIdAsc(
                        ORG_ID, JobStatus.RUNNING))
                .thenReturn(List.of(job));
        when(jobRepository.findRunningAssignments(ORG_ID))
                .thenReturn(List.of(assignments));
    }

    private void givenRunning() {
        when(jobRepository
                .findByOrganizationIdAndStatusOrderBySubmittedAtAscIdAsc(
                        ORG_ID, JobStatus.RUNNING))
                .thenReturn(List.of());
        when(jobRepository.findRunningAssignments(ORG_ID))
                .thenReturn(List.of());
    }

    private static ResourceFilter noFilter() {
        return new ResourceFilter(null, null, null, null);
    }

    private static Job runningJob(final ResourceType type, final int units) {
        Job job = new Job(ORG_ID, type, units, 16,
                type == ResourceType.CPU ? 1 : null, 1);
        job.setStatus(JobStatus.RUNNING);
        return job;
    }

    private static Resource cpu(final long id, final int memoryGb,
            final int cores, final boolean online) {
        return withId(new Resource(ORG_ID, ResourceType.CPU, memoryGb,
                cores, online), id);
    }

    private static Resource gpu(final long id, final int memoryGb,
            final boolean online) {
        return withId(new Resource(ORG_ID, ResourceType.GPU, memoryGb,
                null, online), id);
    }
    // END AI-generated

    private static Resource withId(final Resource resource, final long id) {
        ReflectionTestUtils.setField(resource, "id", id);
        return resource;
    }
}
