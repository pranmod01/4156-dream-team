package com.resourceallocator.controller;

import com.resourceallocator.auth.ApiKeyAuthInterceptor;
import com.resourceallocator.dto.JobQueueFilter;
import com.resourceallocator.dto.JobQueueResponse;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.service.JobQueueService;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint for viewing an organization's job queue. Requests need an
 * {@code X-API-Key} header.
 */
@RestController
public class JobQueueController {

    /** Builds the job queue view. */
    private final JobQueueService jobQueueService;

    /**
     * Creates the controller.
     *
     * @param jobQueueService service that builds the job queue view
     */
    public JobQueueController(final JobQueueService jobQueueService) {
        this.jobQueueService = jobQueueService;
    }

    /**
     * Lists the caller's jobs in queue order with optional filters.
     * Filter values are case-insensitive, so {@code status=queued} and
     * {@code status=QUEUED} are equivalent.
     *
     * @param organization the organization identified by the API key
     * @param status only return jobs in this state
     * @param resourceType only return jobs needing this resource type
     * @param start ISO-8601 start of a time window for running or done jobs
     * @param end ISO-8601 end of a time window for running or done jobs
     * @param limit maximum number of jobs to return
     * @return the matching jobs and status counts
     * @throws InvalidRequestException if a filter value is not recognized
     */
    @GetMapping("/jobs/queue")
    public JobQueueResponse getJobQueue(
            @RequestAttribute(ApiKeyAuthInterceptor.ORGANIZATION_ATTRIBUTE)
            final Organization organization,
            @RequestParam(name = "status", required = false)
            final String status,
            @RequestParam(name = "resourceType", required = false)
            final String resourceType,
            @RequestParam(name = "start", required = false)
            final Instant start,
            @RequestParam(name = "end", required = false)
            final Instant end,
            @RequestParam(name = "limit", required = false)
            final Integer limit) {
        JobQueueFilter filter = new JobQueueFilter(
                parseEnum(JobStatus.class, "status", status),
                parseEnum(ResourceType.class, "resourceType", resourceType),
                start, end, limit);
        return jobQueueService.getJobQueue(organization, filter);
    }

    private static <E extends Enum<E>> E parseEnum(final Class<E> type,
            final String parameter, final String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid " + parameter + " '"
                    + value + "'; expected one of "
                    + Arrays.toString(type.getEnumConstants()) + ".");
        }
    }
}
