package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.model.ResourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of a request to submit a job.
 *
 * <p>{@code minCores} is required for a CPU job and must be omitted for a
 * GPU job; that cross-field rule is enforced by the service layer.
 *
 * @param resourceType whether the job needs CPUs or GPUs
 * @param unitsRequested number of resources the job needs
 * @param minMemoryGb minimum memory per resource, in gigabytes
 * @param minCores minimum cores per CPU; omitted for a GPU job
 * @param maxDurationHours maximum lease length, in hours
 */
public record AddJobRequest(
        @NotNull ResourceType resourceType,
        @NotNull @Positive Integer unitsRequested,
        @NotNull @Positive Integer minMemoryGb,
        @Positive Integer minCores,
        @NotNull @Positive Integer maxDurationHours) {
}
