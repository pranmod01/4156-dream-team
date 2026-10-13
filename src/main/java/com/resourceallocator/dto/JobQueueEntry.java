package com.resourceallocator.dto;

import com.resourceallocator.model.JobStatus;
import java.time.Instant;

/**
 * One job in a job queue response.
 *
 * @param jobId the job identifier
 * @param status the job's current state
 * @param requiredResources the resources the job needs
 * @param submittedAt when the job was submitted
 * @param startedAt when the job started, or {@code null} if it has not
 * @param leaseEnd when the job's lease ends, or {@code null} if it has
 *                 not started
 * @param queuePosition 1-based position among the organization's queued
 *                      jobs, or {@code null} if the job is not queued
 * @param blocked whether the job cannot currently be scheduled
 * @param blockedReason why the job is blocked, or {@code null} if it
 *                      is not
 */
public record JobQueueEntry(
        Long jobId,
        JobStatus status,
        RequiredResources requiredResources,
        Instant submittedAt,
        Instant startedAt,
        Instant leaseEnd,
        Integer queuePosition,
        boolean blocked,
        BlockedReason blockedReason) {
}
