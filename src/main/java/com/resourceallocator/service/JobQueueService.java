package com.resourceallocator.service;

import com.resourceallocator.dto.BlockedReason;
import com.resourceallocator.dto.JobQueueEntry;
import com.resourceallocator.dto.JobQueueFilter;
import com.resourceallocator.dto.JobQueueResponse;
import com.resourceallocator.dto.JobStatusCounts;
import com.resourceallocator.dto.RequiredResources;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.model.Job;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.ResourceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds an organization's view of its job queue.
 */
@Service
public class JobQueueService {

    /** Reads the organization's jobs. */
    private final JobRepository jobRepository;

    /** Reads the organization's resources. */
    private final ResourceRepository resourceRepository;

    /** Brings job states up to date before the queue is read. */
    private final JobDispatcher jobDispatcher;

    /**
     * Creates the service.
     *
     * @param jobRepository repository used to read jobs
     * @param resourceRepository repository used to read resources
     * @param jobDispatcher dispatcher that updates job states
     */
    public JobQueueService(final JobRepository jobRepository,
            final ResourceRepository resourceRepository,
            final JobDispatcher jobDispatcher) {
        this.jobRepository = jobRepository;
        this.resourceRepository = resourceRepository;
        this.jobDispatcher = jobDispatcher;
    }

    /**
     * Returns the organization's jobs in queue order, filtered as
     * requested.
     *
     * <p>Queue positions are computed across all of the organization's
     * queued jobs before filtering, so a job keeps the same position
     * whichever filters are applied. Each queued job is checked for a
     * {@link BlockedReason}; resources assigned to running jobs count as
     * matching but not free. The time window applies only to running and
     * done jobs; queued jobs have not run, so the window never excludes
     * them. The summary always counts every job of the organization.
     *
     * <p>The dispatcher runs first, as it does for the job endpoints, so
     * the queue never shows a job as queued that would already be running
     * or a running job whose lease has ended. The transaction is not
     * read-only because the dispatcher locks the organization's row and
     * updates jobs.
     *
     * @param organization the authenticated organization
     * @param filter the optional filters to apply
     * @return the matching jobs and the organization's status counts
     * @throws InvalidRequestException if {@code end} is before
     *         {@code start} or {@code limit} is not positive
     */
    @Transactional
    public JobQueueResponse getJobQueue(final Organization organization,
            final JobQueueFilter filter) {
        validate(filter);
        jobDispatcher.dispatch(organization.getId());
        List<Job> jobs = jobRepository
                .findByOrganizationIdOrderBySubmittedAtAscIdAsc(
                        organization.getId());
        List<Resource> onlineResources = resourceRepository
                .findByOrganizationIdAndOnlineTrue(organization.getId());
        // AI-generated (Claude Code, Claude Opus 5.5)
        Set<Long> busyResourceIds = new HashSet<>(jobRepository
                .findResourceIdsHeldByRunningJobs(organization.getId()));
        Map<ResourceType, Integer> unitsInUse = unitsInUse(jobs);
        int maxResults = filter.limit() == null
                ? Integer.MAX_VALUE : filter.limit();

        List<JobQueueEntry> entries = new ArrayList<>();
        int queuePosition = 0;
        for (Job job : jobs) {
            if (entries.size() == maxResults) {
                break;
            }
            Integer position = null;
            BlockedReason blockedReason = null;
            if (job.getStatus() == JobStatus.QUEUED) {
                queuePosition++;
                position = queuePosition;
                blockedReason = findBlockedReason(job, organization,
                        onlineResources, busyResourceIds, unitsInUse)
                        .orElse(null);
            }
            if (matches(job, filter)) {
                entries.add(toEntry(job, position, blockedReason));
            }
        }
        return new JobQueueResponse(entries, countByStatus(jobs));
    }

    private static void validate(final JobQueueFilter filter) {
        if (filter.start() != null && filter.end() != null
                && filter.end().isBefore(filter.start())) {
            throw new InvalidRequestException(
                    "end must not be before start.");
        }
        if (filter.limit() != null && filter.limit() <= 0) {
            throw new InvalidRequestException("limit must be positive.");
        }
    }

    // Sums the units held by running jobs, per resource type.
    private static Map<ResourceType, Integer> unitsInUse(
            final List<Job> jobs) {
        Map<ResourceType, Integer> inUse = new EnumMap<>(ResourceType.class);
        for (Job job : jobs) {
            if (job.getStatus() == JobStatus.RUNNING) {
                inUse.merge(job.getResourceType(), job.getUnitsRequested(),
                        Integer::sum);
            }
        }
        return inUse;
    }

    // BEGIN AI-edited (Claude Code, Claude Opus 5.5): busy-resource check
    // Checks run from most to least lasting: no fitting hardware at all,
    // then the organization's policy limit, then resources that will free
    // up when running jobs finish.
    private static Optional<BlockedReason> findBlockedReason(final Job job,
            final Organization organization,
            final List<Resource> onlineResources,
            final Set<Long> busyResourceIds,
            final Map<ResourceType, Integer> unitsInUse) {
        List<Resource> matching = onlineResources.stream()
                .filter(resource -> satisfies(resource, job))
                .toList();
        if (matching.size() < job.getUnitsRequested()) {
            return Optional.of(BlockedReason.NO_MATCHING_RESOURCE);
        }
        int used = unitsInUse.getOrDefault(job.getResourceType(), 0);
        int limit = usageLimit(organization, job.getResourceType());
        if (used + job.getUnitsRequested() > limit) {
            return Optional.of(BlockedReason.EXCEEDS_USAGE_LIMIT);
        }
        long freeMatching = matching.stream()
                .filter(resource -> !busyResourceIds.contains(resource.getId()))
                .count();
        if (freeMatching < job.getUnitsRequested()) {
            return Optional.of(BlockedReason.RESOURCES_IN_USE);
        }
        return Optional.empty();
    }
    // END AI-edited

    // Whether a resource meets a job's type, memory and core requirements.
    private static boolean satisfies(final Resource resource, final Job job) {
        if (resource.getResourceType() != job.getResourceType()
                || resource.getMemoryGb() < job.getMinMemoryGb()) {
            return false;
        }
        return job.getMinCores() == null
                || (resource.getCores() != null
                        && resource.getCores() >= job.getMinCores());
    }

    private static int usageLimit(final Organization organization,
            final ResourceType type) {
        return type == ResourceType.GPU
                ? organization.getMaxGpus() : organization.getMaxCpus();
    }

    private static boolean matches(final Job job,
            final JobQueueFilter filter) {
        if (filter.status() != null && job.getStatus() != filter.status()) {
            return false;
        }
        if (filter.resourceType() != null
                && job.getResourceType() != filter.resourceType()) {
            return false;
        }
        return job.getStatus() == JobStatus.QUEUED
                || overlapsWindow(job, filter.start(), filter.end());
    }

    // Whether a started job's run, from its start to its lease end,
    // overlaps the window. A missing bound leaves that side open.
    private static boolean overlapsWindow(final Job job, final Instant start,
            final Instant end) {
        if (start == null && end == null) {
            return true;
        }
        Instant startedAt = job.getStartedAt();
        if (startedAt == null) {
            return false;
        }
        Instant leaseEnd = job.getLeaseEnd();
        boolean startsBeforeWindowEnds = end == null
                || !startedAt.isAfter(end);
        boolean endsAfterWindowStarts = start == null || leaseEnd == null
                || !leaseEnd.isBefore(start);
        return startsBeforeWindowEnds && endsAfterWindowStarts;
    }

    private static JobQueueEntry toEntry(final Job job,
            final Integer queuePosition, final BlockedReason blockedReason) {
        return new JobQueueEntry(
                job.getId(),
                job.getStatus(),
                RequiredResources.of(job),
                job.getSubmittedAt(),
                job.getStartedAt(),
                job.getLeaseEnd(),
                queuePosition,
                blockedReason != null,
                blockedReason);
    }

    private static JobStatusCounts countByStatus(final List<Job> jobs) {
        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (Job job : jobs) {
            counts.merge(job.getStatus(), 1L, Long::sum);
        }
        return new JobStatusCounts(
                counts.getOrDefault(JobStatus.QUEUED, 0L),
                counts.getOrDefault(JobStatus.RUNNING, 0L),
                counts.getOrDefault(JobStatus.DONE, 0L));
    }
}
