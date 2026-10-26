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
}
