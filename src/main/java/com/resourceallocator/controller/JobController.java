package com.resourceallocator.controller;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.auth.ApiKeyAuthInterceptor;
import com.resourceallocator.dto.AddJobRequest;
import com.resourceallocator.dto.DeleteJobResponse;
import com.resourceallocator.dto.JobResponse;
import com.resourceallocator.model.Organization;
import com.resourceallocator.service.JobService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Endpoints for submitting, checking, and deleting jobs. Job identifiers
 * in paths are numeric, so they never clash with {@code /jobs/queue}.
 * Every request needs an {@code X-API-Key} header.
 */
@RestController
@RequestMapping("/jobs")
public class JobController {

    /** Performs the job operations. */
    private final JobService jobService;

    /**
     * Creates the controller.
     *
     * @param jobService service that performs job operations
     */
    public JobController(final JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Submits a job for the caller's organization.
     *
     * @param organization the organization identified by the API key
     * @param request the job to submit
     * @return 201 Created with the job, queued or running, and its
     *         location
     */
    @PostMapping
    public ResponseEntity<JobResponse> addJob(
            @RequestAttribute(ApiKeyAuthInterceptor.ORGANIZATION_ATTRIBUTE)
            final Organization organization,
            @Valid @RequestBody final AddJobRequest request) {
        JobResponse created = jobService.addJob(organization, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.jobId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Returns the status of one of the caller's jobs.
     *
     * @param organization the organization identified by the API key
     * @param jobId identifier of the job
     * @return 200 OK with the job
     */
    @GetMapping("/{jobId:\\d+}")
    public JobResponse getJobStatus(
            @RequestAttribute(ApiKeyAuthInterceptor.ORGANIZATION_ATTRIBUTE)
            final Organization organization,
            @PathVariable("jobId") final Long jobId) {
        return jobService.getJobStatus(organization, jobId);
    }

    /**
     * Deletes one of the caller's jobs that is not running.
     *
     * @param organization the organization identified by the API key
     * @param jobId identifier of the job
     * @return 200 OK with the deleted job's identifier
     */
    @DeleteMapping("/{jobId:\\d+}")
    public DeleteJobResponse deleteJob(
            @RequestAttribute(ApiKeyAuthInterceptor.ORGANIZATION_ATTRIBUTE)
            final Organization organization,
            @PathVariable("jobId") final Long jobId) {
        return jobService.deleteJob(organization, jobId);
    }
}
