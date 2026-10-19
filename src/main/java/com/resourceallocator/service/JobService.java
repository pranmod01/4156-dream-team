package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.dto.AddJobRequest;
import com.resourceallocator.dto.DeleteJobResponse;
import com.resourceallocator.dto.JobResponse;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.exception.NotFoundException;
import com.resourceallocator.model.Job;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submits, looks up, and deletes jobs. Each operation first runs the
 * {@link JobDispatcher} so the job states it sees and returns are
 * current.
 */
@Service
public class JobService {

    /** Persists jobs. */
    private final JobRepository jobRepository;

    /** Starts queued jobs and finishes expired ones. */
    private final JobDispatcher jobDispatcher;

    /**
     * Creates the service.
     *
     * @param jobRepository repository used to persist jobs
     * @param jobDispatcher dispatcher that updates job states
     */
    public JobService(final JobRepository jobRepository,
            final JobDispatcher jobDispatcher) {
        this.jobRepository = jobRepository;
        this.jobDispatcher = jobDispatcher;
    }

    /**
     * Queues a new job for the organization and starts it at once if its
     * resources are free.
     *
     * <p>A request is rejected only if it could never run: a CPU job
     * without a core count, a GPU job with one, or more units than the
     * organization's limit for that type. A job that fits the limit but
     * cannot start yet stays queued.
     *
     * @param organization the authenticated organization
     * @param request the job to submit
     * @return the job, queued or running
     * @throws InvalidRequestException if the request breaks one of the
     *         rules above
     */
    @Transactional
    public JobResponse addJob(final Organization organization,
            final AddJobRequest request) {
        validateCores(request.resourceType(), request.minCores());
        int limit = usageLimit(organization, request.resourceType());
        if (request.unitsRequested() > limit) {
            throw new InvalidRequestException("unitsRequested exceeds the "
                    + "organization's " + request.resourceType()
                    + " limit of " + limit + ".");
        }
        Job job = jobRepository.saveAndFlush(new Job(
                organization.getId(),
                request.resourceType(),
                request.unitsRequested(),
                request.minMemoryGb(),
                request.minCores(),
                request.maxDurationHours()));
        jobDispatcher.dispatch(organization.getId());
        return toResponse(job);
    }

    /**
     * Returns one of the organization's jobs and its current status.
     *
     * @param organization the authenticated organization
     * @param jobId identifier of the job
     * @return the job
     * @throws InvalidRequestException if the organization has no job with
     *         that identifier
     */
    @Transactional
    public JobResponse getJobStatus(final Organization organization,
            final Long jobId) {
        jobDispatcher.dispatch(organization.getId());
        Job job = jobRepository
                .findByIdAndOrganizationId(jobId, organization.getId())
                .orElseThrow(() -> new InvalidRequestException(
                        "Job " + jobId + " was not found."));
        return toResponse(job);
    }

    /**
     * Permanently deletes one of the organization's jobs that is not
     * running. Any resource assignments of the job are deleted with it.
     *
     * @param organization the authenticated organization
     * @param jobId identifier of the job
     * @return confirmation naming the deleted job
     * @throws NotFoundException if the organization has no job with that
     *         identifier
     * @throws ConflictException if the job is running
     */
    @Transactional
    public DeleteJobResponse deleteJob(final Organization organization,
            final Long jobId) {
        jobDispatcher.dispatch(organization.getId());
        Job job = jobRepository
                .findByIdAndOrganizationId(jobId, organization.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Job " + jobId + " was not found."));
        if (job.getStatus() == JobStatus.RUNNING) {
            throw new ConflictException(
                    "Job " + jobId + " is running and cannot be deleted.");
        }
        jobRepository.delete(job);
        return new DeleteJobResponse(jobId);
    }

    private JobResponse toResponse(final Job job) {
        return JobResponse.from(job,
                jobRepository.findResourceIdsByJobId(job.getId()));
    }

    private static void validateCores(final ResourceType type,
            final Integer minCores) {
        if (type == ResourceType.CPU && minCores == null) {
            throw new InvalidRequestException(
                    "minCores is required for CPU jobs.");
        }
        if (type == ResourceType.GPU && minCores != null) {
            throw new InvalidRequestException(
                    "minCores must not be set for GPU jobs.");
        }
    }

    private static int usageLimit(final Organization organization,
            final ResourceType type) {
        return type == ResourceType.GPU
                ? organization.getMaxGpus() : organization.getMaxCpus();
    }
}
