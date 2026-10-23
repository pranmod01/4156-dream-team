package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.model.Job;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.OrganizationRepository;
import com.resourceallocator.repository.ResourceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves an organization's jobs through their lifecycle: finishes running
 * jobs whose lease has ended, then starts queued jobs whose resources are
 * free.
 *
 * <p>Queued jobs are considered oldest first. A job starts only if
 * enough free online resources meet its requirements and the units it
 * needs, added to the units held by running jobs of the same type, stay
 * within the organization's usage limit. A job that cannot start stays
 * queued, and later jobs may still start ahead of it.
 */
@Service
public class JobDispatcher {

    /** Reads and updates jobs and their resource assignments. */
    private final JobRepository jobRepository;

    /** Reads the organization's resources. */
    private final ResourceRepository resourceRepository;

    /** Reads and locks organizations. */
    private final OrganizationRepository organizationRepository;

    /**
     * Creates the dispatcher.
     *
     * @param jobRepository repository used to read and update jobs
     * @param resourceRepository repository used to read resources
     * @param organizationRepository repository used to lock organizations
     */
    public JobDispatcher(final JobRepository jobRepository,
            final ResourceRepository resourceRepository,
            final OrganizationRepository organizationRepository) {
        this.jobRepository = jobRepository;
        this.resourceRepository = resourceRepository;
        this.organizationRepository = organizationRepository;
    }

    /**
     * Dispatches the organization's jobs as of the current time.
     *
     * @param organizationId the organization whose jobs to dispatch
     */
    @Transactional
    public void dispatch(final Long organizationId) {
        dispatch(organizationId, Instant.now());
    }

    /**
     * Dispatches the organization's jobs as of the given time. The
     * organization's row stays locked until the transaction ends, so
     * concurrent runs for one organization cannot assign a resource
     * twice.
     *
     * @param organizationId the organization whose jobs to dispatch
     * @param now the time to treat as the present
     */
    @Transactional
    public void dispatch(final Long organizationId, final Instant now) {
        // Serializes dispatches per organization: findByIdForUpdate holds a
        // pessimistic lock on the organization row until the transaction
        // commits.
        Optional<Organization> organization =
                organizationRepository.findByIdForUpdate(organizationId);
        if (organization.isEmpty()) {
            return;
        }
        // Release finished jobs to update available resources
        Map<ResourceType, Integer> unitsInUse =
                finishExpiredLeases(organizationId, now);
        Set<Long> busyResourceIds = new HashSet<>(jobRepository
                .findResourceIdsHeldByRunningJobs(organizationId));
        List<Resource> onlineResources = new ArrayList<>(resourceRepository
                .findByOrganizationIdAndOnlineTrue(organizationId));
        onlineResources.sort(Comparator.comparing(Resource::getId));

        for (Job job : jobRepository
                .findByOrganizationIdAndStatusOrderBySubmittedAtAscIdAsc(
                        organizationId, JobStatus.QUEUED)) {
            // Skip jobs that would exceed usage limit
            int used = unitsInUse.getOrDefault(job.getResourceType(), 0);
            if (used + job.getUnitsRequested()
                    > usageLimit(organization.get(), job.getResourceType())) {
                continue;
            }
            // Get list of suitable units for this job, try to assign them
            List<Resource> assigned = onlineResources.stream()
                    .filter(resource -> !busyResourceIds.contains(
                            resource.getId()))
                    .filter(resource -> satisfies(resource, job))
                    .limit(job.getUnitsRequested())
                    .toList();
            if (assigned.size() < job.getUnitsRequested()) {
                continue;
            }
            for (Resource resource : assigned) {
                // Assignment is only flushed at end of transaction
                // Reads can be stale, organization lock
                jobRepository.assignResource(job.getId(), resource.getId());
                busyResourceIds.add(resource.getId());
            }
            // Updates job status. Doesn't flush until after transaction
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(now);
            job.setLeaseEnd(now.plus(
                    Duration.ofHours(job.getMaxDurationHours())));
            unitsInUse.merge(job.getResourceType(), job.getUnitsRequested(),
                    Integer::sum);
        }
    }

    /**
     * Locks the organization, finishes expired leases, and reports whether
     * a running job holds the resource. Must run inside the caller's
     * transaction, so the lock is held until the caller commits and no
     * dispatch can assign the resource before then.
     *
     * @param organizationId the organization that owns the resource
     * @param resourceId the resource to check
     * @return whether a running job holds the resource
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockAndCheckResourceInUse(final Long organizationId,
            final Long resourceId) {
        // Holds lock on organization, preventing other dispatches
        organizationRepository.findByIdForUpdate(organizationId);
        finishExpiredLeases(organizationId, Instant.now());
        return jobRepository.findResourceIdsHeldByRunningJobs(organizationId)
                .contains(resourceId);
    }

    // Marks running jobs whose lease has ended as done, and returns the
    // units still held by the remaining running jobs, per resource type.
    private Map<ResourceType, Integer> finishExpiredLeases(
            final Long organizationId, final Instant now) {
        Map<ResourceType, Integer> unitsInUse =
                new EnumMap<>(ResourceType.class);
        for (Job job : jobRepository
                .findByOrganizationIdAndStatusOrderBySubmittedAtAscIdAsc(
                        organizationId, JobStatus.RUNNING)) {
            if (job.getLeaseEnd() != null && !job.getLeaseEnd().isAfter(now)) {
                job.setStatus(JobStatus.DONE);
            } else {
                unitsInUse.merge(job.getResourceType(),
                        job.getUnitsRequested(), Integer::sum);
            }
        }
        // Write the finished jobs out so the busy-resource query, which
        // reads the database directly, no longer counts them.
        jobRepository.flush();
        return unitsInUse;
    }

    // Whether a resource meets a job's type, memory and core requirements.
    private static boolean satisfies(final Resource resource, final Job job) {
        if (resource.getResourceType() != job.getResourceType()
                || resource.getMemoryGb() < job.getMinMemoryGb()) {
            return false;
        }
        return job.getMinCores() == null
                || resource.getCores() != null
                        && resource.getCores() >= job.getMinCores();
    }

    private static int usageLimit(final Organization organization,
            final ResourceType type) {
        return type == ResourceType.GPU
                ? organization.getMaxGpus() : organization.getMaxCpus();
    }
}
