package com.resourceallocator.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of a request to take a resource online or offline.
 *
 * @param online {@code true} to make the resource schedulable,
 *               {@code false} to take it offline
 */
public record UpdateResourceRequest(@NotNull Boolean online) {
}
