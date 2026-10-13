package com.resourceallocator.dto;

import com.resourceallocator.model.ResourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of a request to register a new resource.
 *
 * <p>{@code cores} is required for a CPU and must be omitted for a GPU;
 * that cross-field rule is enforced by the service layer.
 *
 * @param resourceType whether the resource is a CPU or a GPU
 * @param memoryGb memory available, in gigabytes
 * @param cores core count for a CPU; omitted for a GPU
 * @param online whether the resource can be scheduled right away
 */
public record AddResourceRequest(
        @NotNull ResourceType resourceType,
        @NotNull @Positive Integer memoryGb,
        @Positive Integer cores,
        @NotNull Boolean online) {
}
