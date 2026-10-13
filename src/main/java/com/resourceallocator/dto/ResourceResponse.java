package com.resourceallocator.dto;

import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;

/**
 * A resource as returned to API clients.
 *
 * @param id the resource identifier
 * @param resourceType whether the resource is a CPU or a GPU
 * @param memoryGb memory available, in gigabytes
 * @param cores core count for a CPU, or {@code null} for a GPU
 * @param online whether the resource can currently be scheduled
 */
public record ResourceResponse(
        Long id,
        ResourceType resourceType,
        int memoryGb,
        Integer cores,
        boolean online) {

    /**
     * Builds the response view of a resource entity.
     *
     * @param resource the resource entity
     * @return the response view
     */
    public static ResourceResponse from(final Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getResourceType(),
                resource.getMemoryGb(),
                resource.getCores(),
                resource.isOnline());
    }
}
