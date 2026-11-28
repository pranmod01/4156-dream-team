package com.resourceallocator.dto;

import java.util.List;

/**
 * Result of a job queue query.
 *
 * @param jobs matching jobs in queue order (oldest submission first)
 * @param summary counts by state across all of the organization's jobs,
 *                independent of the filters
 */
public record JobQueueResponse(
        List<JobQueueEntry> jobs,
        JobStatusCounts summary) {

    // AI-generated (Claude Code, Claude Opus 5.5): defensive copy (SpotBugs
    // EI_EXPOSE_REP / EI_EXPOSE_REP2)
    /**
     * Copies {@code jobs} into an unmodifiable list, so the response
     * cannot be changed through the list it was built from or the list
     * it returns.
     *
     * @param jobs matching jobs in queue order
     * @param summary counts by state across all of the organization's jobs
     */
    public JobQueueResponse {
        jobs = List.copyOf(jobs);
    }
}
