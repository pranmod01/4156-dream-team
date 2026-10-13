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
}
