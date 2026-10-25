package com.resourceallocator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resourceallocator.dto.AddResourceRequest;
import com.resourceallocator.dto.DeleteResourceResponse;
import com.resourceallocator.dto.ResourceResponse;
import com.resourceallocator.dto.UpdateResourceRequest;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.exception.NotFoundException;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
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

    private ResourceService resourceService;

    private Organization organization;

    @BeforeEach
    void setUp() {
        resourceService = new ResourceService(resourceRepository,
                jobDispatcher);
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
    void listResourcesReturnsAllOfTheOrganizationsResources() {
        when(resourceRepository.findByOrganizationIdOrderByIdAsc(ORG_ID))
                .thenReturn(List.of(
                        withId(new Resource(ORG_ID, ResourceType.CPU, 32, 8,
                                true), 1L),
                        withId(new Resource(ORG_ID, ResourceType.GPU, 80,
                                null, false), 2L)));

        List<ResourceResponse> responses =
                resourceService.listResources(organization);

        assertThat(responses).containsExactly(
                new ResourceResponse(1L, ResourceType.CPU, 32, 8, true),
                new ResourceResponse(2L, ResourceType.GPU, 80, null, false));
    }
    // END AI-generated

    // BEGIN AI-generated (Claude, Claude Opus 5.5), reviewed by Rob
    @Test
    void deleteResourceDeletesFreeResource() {
        Resource resource = withId(
                new Resource(ORG_ID, ResourceType.GPU, 80, null, true), 5L);
        when(resourceRepository.findByIdAndOrganizationId(5L, ORG_ID))
                .thenReturn(Optional.of(resource));
        when(jobDispatcher.lockAndCheckResourceInUse(ORG_ID, 5L))
                .thenReturn(false);

        DeleteResourceResponse response =
                resourceService.deleteResource(organization, 5L);

        assertThat(response.deletedResourceId()).isEqualTo(5L);
        verify(resourceRepository).delete(resource);
    }

    @Test
    void deleteResourceRejectsResourceInUse() {
        Resource resource = withId(
                new Resource(ORG_ID, ResourceType.GPU, 80, null, true), 5L);
        when(resourceRepository.findByIdAndOrganizationId(5L, ORG_ID))
                .thenReturn(Optional.of(resource));
        when(jobDispatcher.lockAndCheckResourceInUse(ORG_ID, 5L))
                .thenReturn(true);

        assertThatThrownBy(() ->
                resourceService.deleteResource(organization, 5L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("5");
        verify(resourceRepository, never()).delete(any(Resource.class));
    }

    @Test
    void deleteResourceRejectsUnknownOrForeignResource() {
        when(resourceRepository.findByIdAndOrganizationId(99L, ORG_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                resourceService.deleteResource(organization, 99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
        verify(resourceRepository, never()).delete(any(Resource.class));
    }

    @Test
    void deleteResourceLocksBeforeLookingUpTheResource() {
        Resource resource = withId(
                new Resource(ORG_ID, ResourceType.GPU, 80, null, true), 5L);
        when(resourceRepository.findByIdAndOrganizationId(5L, ORG_ID))
                .thenReturn(Optional.of(resource));

        resourceService.deleteResource(organization, 5L);

        InOrder order = inOrder(jobDispatcher, resourceRepository);
        order.verify(jobDispatcher).lockAndCheckResourceInUse(ORG_ID, 5L);
        order.verify(resourceRepository)
                .findByIdAndOrganizationId(5L, ORG_ID);
        order.verify(resourceRepository).delete(resource);
    }
    // END AI-generated

    private static Resource withId(final Resource resource, final long id) {
        ReflectionTestUtils.setField(resource, "id", id);
        return resource;
    }
}
