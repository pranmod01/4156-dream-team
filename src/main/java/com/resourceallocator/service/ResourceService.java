package com.resourceallocator.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers, lists, and deletes resources and changes their online
 * state.
 */
@Service
public class ResourceService {

    /** Persists resources. */
    private final ResourceRepository resourceRepository;

    /** Checks whether running jobs hold a resource. */
    private final JobDispatcher jobDispatcher;

    /**
     * Creates the service.
     *
     * @param resourceRepository repository used to persist resources
     * @param jobDispatcher dispatcher used to check resource usage
     */
    public ResourceService(final ResourceRepository resourceRepository,
            final JobDispatcher jobDispatcher) {
        this.resourceRepository = resourceRepository;
        this.jobDispatcher = jobDispatcher;
    }

    /**
     * Registers a new resource owned by the given organization.
     *
     * @param organization the authenticated organization
     * @param request the resource to create
     * @return the created resource, including its new identifier
     * @throws InvalidRequestException if a CPU has no core count or a GPU
     *         has one
     */
    @Transactional
    public ResourceResponse addResource(final Organization organization,
            final AddResourceRequest request) {
        validateCores(request.resourceType(), request.cores());
        Resource resource = new Resource(
                organization.getId(),
                request.resourceType(),
                request.memoryGb(),
                request.cores(),
                request.online());
        return ResourceResponse.from(resourceRepository.save(resource));
    }

    /**
     * Takes one of the organization's resources online or offline.
     *
     * @param organization the authenticated organization
     * @param resourceId identifier of the resource to update
     * @param request the new online state
     * @return the updated resource
     * @throws NotFoundException if the organization has no resource
     *         with that identifier
     * @throws ConflictException if attempting to take a resource that
     *         is in use offline
     */
    @Transactional
    public ResourceResponse updateResource(final Organization organization,
            final Long resourceId, final UpdateResourceRequest request) {
        Resource resource = resourceRepository
                .findByIdAndOrganizationId(resourceId, organization.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Resource " + resourceId + " was not found."));
        // A resource held by a running job cannot be taken offline.
        if (!request.online() && jobDispatcher.lockAndCheckResourceInUse(
                organization.getId(), resourceId)) {
            throw new ConflictException("Resource " + resourceId
                    + " is in use by a running job.");
        }
        resource.setOnline(request.online());
        return ResourceResponse.from(resource);
    }

    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    /**
     * Lists every resource owned by the organization, online or offline.
     *
     * @param organization the authenticated organization
     * @return the organization's resources, ordered by identifier
     */
    @Transactional(readOnly = true)
    public List<ResourceResponse> listResources(
            final Organization organization) {
        return resourceRepository
                .findByOrganizationIdOrderByIdAsc(organization.getId())
                .stream()
                .map(ResourceResponse::from)
                .toList();
    }
    // END AI-generated

    // BEGIN AI-generated (Claude, Claude Opus 5.5), reviewed by Rob
    /**
     * Permanently deletes one of the organization's resources. A resource
     * held by a running job cannot be deleted. Its assignments to finished
     * jobs are removed with it (the database cascades them).
     *
     * <p>The organization is locked before the resource is looked up, so
     * no dispatch can assign the resource while the delete runs, and a
     * second delete of the same resource waits and then gets 404 instead
     * of failing.
     *
     * @param organization the authenticated organization
     * @param resourceId identifier of the resource to delete
     * @return confirmation with the deleted resource's identifier
     * @throws NotFoundException if the organization has no resource with
     *         that identifier
     * @throws ConflictException if a running job holds the resource
     */
    @Transactional
    public DeleteResourceResponse deleteResource(
            final Organization organization, final Long resourceId) {
        boolean inUse = jobDispatcher.lockAndCheckResourceInUse(
                organization.getId(), resourceId);
        Resource resource = resourceRepository
                .findByIdAndOrganizationId(resourceId, organization.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Resource " + resourceId + " was not found."));
        if (inUse) {
            throw new ConflictException("Resource " + resourceId
                    + " is in use by a running job and cannot be deleted.");
        }
        resourceRepository.delete(resource);
        return new DeleteResourceResponse(resourceId);
    }
    // END AI-generated

    private static void validateCores(final ResourceType type,
            final Integer cores) {
        if (type == ResourceType.CPU && cores == null) {
            throw new InvalidRequestException(
                    "cores is required for CPU resources.");
        }
        if (type == ResourceType.GPU && cores != null) {
            throw new InvalidRequestException(
                    "cores must not be set for GPU resources.");
        }
    }
}
