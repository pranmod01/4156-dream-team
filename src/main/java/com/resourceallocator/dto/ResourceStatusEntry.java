package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;

/**
 * One resource in a resource status query.
 *
 * @param id resource identifier
 * @param resourceType CPU or GPU
 * @param memoryGb memory in gigabytes
 * @param cores core count for CPUs; {@code null} for GPUs
 * @param online whether the organization has the resource online
 * @param status whether the resource is available, in use, or offline
 * @param runningJobId the running job holding the resource, or
 *                     {@code null} if none
 */
public record ResourceStatusEntry(
        Long id,
        ResourceType resourceType,
        int memoryGb,
        Integer cores,
        boolean online,
        ResourceStatus status,
        Long runningJobId) {

    /**
     * Builds the status view of a resource.
     *
     * @param resource the resource entity
     * @param runningJobId the running job holding it, or {@code null}
     * @return the status view
     */
    public static ResourceStatusEntry from(final Resource resource,
            final Long runningJobId) {
        ResourceStatus status;
        if (runningJobId != null) {
            status = ResourceStatus.IN_USE;
        } else if (resource.isOnline()) {
            status = ResourceStatus.AVAILABLE;
        } else {
            status = ResourceStatus.OFFLINE;
        }
        return new ResourceStatusEntry(
                resource.getId(),
                resource.getResourceType(),
                resource.getMemoryGb(),
                resource.getCores(),
                resource.isOnline(),
                status,
                runningJobId);
    }
}
