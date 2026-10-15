package com.resourceallocator.dto;

import com.resourceallocator.model.Job;
import com.resourceallocator.model.ResourceType;

/**
 * The resources a job needs in order to run.
 *
 * @param resourceType type of resource needed
 * @param unitsRequested number of resources needed
 * @param minMemoryGb minimum memory per resource, in gigabytes
 * @param minCores minimum cores per CPU, or {@code null} for GPU jobs
 * @param maxDurationHours maximum lease length, in hours
 */
public record RequiredResources(
        ResourceType resourceType,
        int unitsRequested,
        int minMemoryGb,
        Integer minCores,
        int maxDurationHours) {

    /**
     * Extracts the resource requirements of a job.
     *
     * @param job the job
     * @return the job's resource requirements
     */
    public static RequiredResources of(final Job job) {
        return new RequiredResources(
                job.getResourceType(),
                job.getUnitsRequested(),
                job.getMinMemoryGb(),
                job.getMinCores(),
                job.getMaxDurationHours());
    }
}
