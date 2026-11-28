package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

import java.util.List;

/**
 * Result of a resource status query.
 *
 * @param resources matching resources, ordered by identifier
 * @param summary usage against limits across all of the organization's
 *                resources, independent of the filters
 */
public record ResourceListResponse(
        List<ResourceStatusEntry> resources,
        ResourceSummary summary) {

    // AI-generated (Claude Code, Claude Opus 5.5): defensive copy (SpotBugs
    // EI_EXPOSE_REP / EI_EXPOSE_REP2)
    /**
     * Copies {@code resources} into an unmodifiable list, so the response
     * cannot be changed through the list it was built from or the list
     * it returns.
     *
     * @param resources matching resources, ordered by identifier
     * @param summary usage against limits across all of the organization's
     *                resources
     */
    public ResourceListResponse {
        resources = List.copyOf(resources);
    }
}
