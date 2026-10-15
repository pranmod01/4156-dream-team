package com.resourceallocator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resourceallocator.dto.AddResourceRequest;
import com.resourceallocator.dto.ResourceResponse;
import com.resourceallocator.dto.UpdateResourceRequest;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.ResourceRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    private static final long ORG_ID = 7L;

    @Mock
    private ResourceRepository resourceRepository;

    private ResourceService resourceService;

    private Organization organization;

    @BeforeEach
    void setUp() {
        resourceService = new ResourceService(resourceRepository);
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
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("99");
    }

    private static Resource withId(final Resource resource, final long id) {
        ReflectionTestUtils.setField(resource, "id", id);
        return resource;
    }
}
