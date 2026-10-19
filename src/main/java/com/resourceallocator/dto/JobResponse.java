package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.model.Job;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.ResourceType;
import java.time.Instant;
import java.util.List;

/**
 * A job and its current status as returned to API clients.
 *
 * @param jobId the job identifier
 * @param status whether the job is queued, running, or done
 * @param resourceType whether the job needs CPUs or GPUs
 * @param unitsRequested number of resources the job needs
 * @param submittedAt when the job was submitted
 * @param startedAt when the job started running, or {@code null}
 * @param leaseEnd when the job's lease ends, or {@code null}
 * @param resourceIds identifiers of the resources assigned to the job;
 *        empty while it is queued
 */
public record JobResponse(
        Long jobId,
        JobStatus status,
        ResourceType resourceType,
        int unitsRequested,
        Instant submittedAt,
        Instant startedAt,
        Instant leaseEnd,
        List<Long> resourceIds) {

    /**
     * Builds the response view of a job entity.
     *
     * @param job the job entity
     * @param resourceIds identifiers of the resources assigned to the job
     * @return the response view
     */
    public static JobResponse from(final Job job,
            final List<Long> resourceIds) {
        return new JobResponse(
                job.getId(),
                job.getStatus(),
                job.getResourceType(),
                job.getUnitsRequested(),
                job.getSubmittedAt(),
                job.getStartedAt(),
                job.getLeaseEnd(),
                resourceIds);
    }
}
