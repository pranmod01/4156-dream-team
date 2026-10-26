package com.resourceallocator.service;

import com.resourceallocator.dto.AddResourceRequest;
import com.resourceallocator.dto.ResourceFilter;
import com.resourceallocator.dto.ResourceListResponse;
import com.resourceallocator.dto.ResourceResponse;
import com.resourceallocator.dto.ResourceStatusEntry;
import com.resourceallocator.dto.ResourceSummary;
import com.resourceallocator.dto.ResourceUsage;
import com.resourceallocator.dto.UpdateResourceRequest;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.exception.NotFoundException;
import com.resourceallocator.model.Job;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.ResourceRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers resources, reports their status, and changes their online
 * state.
 */
@Service
public class ResourceService {

    /** Persists resources. */
    private final ResourceRepository resourceRepository;

    /** Checks whether running jobs hold a resource. */
    private final JobDispatcher jobDispatcher;

    /** Reads running jobs and their resource assignments. */
    private final JobRepository jobRepository;

    /**
     * Creates the service.
     *
     * @param resourceRepository repository used to persist resources
     * @param jobDispatcher dispatcher used to check resource usage
     * @param jobRepository repository used to read running jobs
     */
    public ResourceService(final ResourceRepository resourceRepository,
            final JobDispatcher jobDispatcher,
            final JobRepository jobRepository) {
        this.resourceRepository = resourceRepository;
        this.jobDispatcher = jobDispatcher;
        this.jobRepository = jobRepository;
    }

    /**
     * Registers a new resource owned by the given organization.
     *
     * @param organization the authenticated organization
     * @param request the resource to create
     * @return the created resource, including its new identifier
     * @throws InvalidRequestException if a CPU has no core count or a GPU
     *         has one
     */
    @Transactional
    public ResourceResponse addResource(final Organization organization,
            final AddResourceRequest request) {
        validateCores(request.resourceType(), request.cores());
        Resource resource = new Resource(
                organization.getId(),
                request.resourceType(),
                request.memoryGb(),
                request.cores(),
                request.online());
        return ResourceResponse.from(resourceRepository.save(resource));
    }

    /**
     * Takes one of the organization's resources online or offline.
     *
     * @param organization the authenticated organization
     * @param resourceId identifier of the resource to update
     * @param request the new online state
     * @return the updated resource
     * @throws NotFoundException if the organization has no resource
     *         with that identifier
     * @throws ConflictException if attempting to take a resource that
     *         is in use offline
     */
    @Transactional
    public ResourceResponse updateResource(final Organization organization,
            final Long resourceId, final UpdateResourceRequest request) {
        Resource resource = resourceRepository
                .findByIdAndOrganizationId(resourceId, organization.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Resource " + resourceId + " was not found."));
        // A resource held by a running job cannot be taken offline.
        if (!request.online() && jobDispatcher.lockAndCheckResourceInUse(
                organization.getId(), resourceId)) {
            throw new ConflictException("Resource " + resourceId
                    + " is in use by a running job.");
        }
        resource.setOnline(request.online());
        return ResourceResponse.from(resource);
    }

    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    /**
     * Lists the organization's resources that match the filter, each
     * with its current status and the running job holding it, plus the
     * organization's usage against its limits.
     *
     * <p>The dispatcher runs first, as it does for the job endpoints, so
     * statuses reflect ended leases and newly started jobs. The summary
     * always covers all of the organization's resources and running jobs,
     * whichever filters are applied.
     *
     * @param organization the authenticated organization
     * @param filter the optional filters to apply
     * @return the matching resources, ordered by identifier, and the
     *         usage summary
     * @throws InvalidRequestException if {@code minMemoryGb} or
     *         {@code minCores} is not positive
     * @throws NotFoundException if {@code id} is set and the
     *         organization has no resource with that identifier
     */
    @Transactional
    public ResourceListResponse listResources(
            final Organization organization, final ResourceFilter filter) {
        validate(filter);
        jobDispatcher.dispatch(organization.getId());
        List<Resource> resources = resourceRepository
                .findByOrganizationIdOrderByIdAsc(organization.getId());
        if (filter.id() != null && resources.stream()
                .noneMatch(resource -> resource.getId().equals(filter.id()))) {
            throw new NotFoundException(
                    "Resource " + filter.id() + " was not found.");
        }
        Map<Long, Long> runningJobByResource = new HashMap<>();
        for (Object[] row : jobRepository
                .findRunningAssignments(organization.getId())) {
            runningJobByResource.put(((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue());
        }
        List<ResourceStatusEntry> entries = resources.stream()
                .filter(resource -> matches(resource, filter))
                .map(resource -> ResourceStatusEntry.from(resource,
                        runningJobByResource.get(resource.getId())))
                .toList();
        List<Job> runningJobs = jobRepository
                .findByOrganizationIdAndStatusOrderBySubmittedAtAscIdAsc(
                        organization.getId(), JobStatus.RUNNING);
        ResourceSummary summary = new ResourceSummary(
                usage(ResourceType.CPU, organization.getMaxCpus(),
                        runningJobs, resources, runningJobByResource),
                usage(ResourceType.GPU, organization.getMaxGpus(),
                        runningJobs, resources, runningJobByResource));
        return new ResourceListResponse(entries, summary);
    }

    private static void validate(final ResourceFilter filter) {
        if (filter.minMemoryGb() != null && filter.minMemoryGb() <= 0) {
            throw new InvalidRequestException(
                    "minMemoryGb must be positive.");
        }
        if (filter.minCores() != null && filter.minCores() <= 0) {
            throw new InvalidRequestException("minCores must be positive.");
        }
    }

    private static boolean matches(final Resource resource,
            final ResourceFilter filter) {
        if (filter.id() != null && !resource.getId().equals(filter.id())) {
            return false;
        }
        if (filter.resourceType() != null
                && resource.getResourceType() != filter.resourceType()) {
            return false;
        }
        if (filter.minMemoryGb() != null
                && resource.getMemoryGb() < filter.minMemoryGb()) {
            return false;
        }
        // Only CPUs have cores, so a core filter excludes every GPU.
        return filter.minCores() == null
                || (resource.getCores() != null
                        && resource.getCores() >= filter.minCores());
    }

    // Units held by running jobs of one type against the limit, and how
    // many resources of that type are online and not held by a job.
    private static ResourceUsage usage(final ResourceType type,
            final int limit, final List<Job> runningJobs,
            final List<Resource> resources,
            final Map<Long, Long> runningJobByResource) {
        int inUse = runningJobs.stream()
                .filter(job -> job.getResourceType() == type)
                .mapToInt(Job::getUnitsRequested)
                .sum();
        long free = resources.stream()
                .filter(resource -> resource.getResourceType() == type
                        && resource.isOnline()
                        && !runningJobByResource.containsKey(resource.getId()))
                .count();
        return new ResourceUsage(limit, inUse, Math.max(0, limit - inUse),
                free);
    }
    // END AI-generated

    private static void validateCores(final ResourceType type,
            final Integer cores) {
        if (type == ResourceType.CPU && cores == null) {
            throw new InvalidRequestException(
                    "cores is required for CPU resources.");
        }
        if (type == ResourceType.GPU && cores != null) {
            throw new InvalidRequestException(
                    "cores must not be set for GPU resources.");
        }
    }
}
