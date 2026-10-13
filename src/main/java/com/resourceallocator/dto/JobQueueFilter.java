package com.resourceallocator.dto;

import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.ResourceType;
import java.time.Instant;

/**
 * Optional filters for a job queue query. Every component may be
 * {@code null}, meaning "do not filter on this".
 *
 * @param status only include jobs in this state
 * @param resourceType only include jobs needing this resource type
 * @param start only include running or done jobs whose run overlaps a
 *              window starting at this time
 * @param end only include running or done jobs whose run overlaps a
 *            window ending at this time
 * @param limit maximum number of jobs to return
 */
public record JobQueueFilter(
        JobStatus status,
        ResourceType resourceType,
        Instant start,
        Instant end,
        Integer limit) {
}
