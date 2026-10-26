package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.model.ResourceType;

/**
 * Optional filters for a resource status query. Every component may be
 * {@code null}, meaning "do not filter on this".
 *
 * @param id only include the resource with this identifier
 * @param resourceType only include resources of this type
 * @param minMemoryGb only include resources with at least this much
 *                    memory
 * @param minCores only include CPUs with at least this many cores
 */
public record ResourceFilter(
        Long id,
        ResourceType resourceType,
        Integer minMemoryGb,
        Integer minCores) {
}
