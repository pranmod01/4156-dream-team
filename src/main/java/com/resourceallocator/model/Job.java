package com.resourceallocator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A request by an organization to run work on one or more resources.
 */
@Entity
@Table(name = "job")
public class Job {

    /** Database-generated identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifier of the organization that submitted the job. */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    /** Type of resource the job needs. */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, updatable = false,
            length = 10)
    private ResourceType resourceType;

    /** Number of resources of {@link #resourceType} the job needs. */
    @Column(name = "units_requested", nullable = false, updatable = false)
    private int unitsRequested;

    /** Minimum memory each resource must have, in gigabytes. */
    @Column(name = "min_memory_gb", nullable = false, updatable = false)
    private int minMemoryGb;

    /** Minimum cores each CPU must have; {@code null} for GPU jobs. */
    @Column(name = "min_cores", updatable = false)
    private Integer minCores;

    /** Maximum lease length once the job starts, in hours. */
    @Column(name = "max_duration_hours", nullable = false, updatable = false)
    private int maxDurationHours;

    /** Current lifecycle state of the job. */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false, length = 10)
    private JobStatus status;

    /** When the job was submitted; determines its queue position. */
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    /** When the job started running, or {@code null} if it has not. */
    @Column(name = "started_at")
    private Instant startedAt;

    /** When the job's lease ends, or {@code null} if it has not started. */
    @Column(name = "lease_end")
    private Instant leaseEnd;

    /**
     * Creates an empty job. Required by JPA.
     */
    protected Job() {
        // Required by JPA.
    }

    /**
     * Creates a new queued job submitted at the current time.
     *
     * @param organizationId identifier of the submitting organization
     * @param resourceType type of resource the job needs
     * @param unitsRequested number of resources the job needs
     * @param minMemoryGb minimum memory per resource, in gigabytes
     * @param minCores minimum cores per CPU, or {@code null} for GPU jobs
     * @param maxDurationHours maximum lease length, in hours
     */
    public Job(final Long organizationId, final ResourceType resourceType,
            final int unitsRequested, final int minMemoryGb,
            final Integer minCores, final int maxDurationHours) {
        this.organizationId = organizationId;
        this.resourceType = resourceType;
        this.unitsRequested = unitsRequested;
        this.minMemoryGb = minMemoryGb;
        this.minCores = minCores;
        this.maxDurationHours = maxDurationHours;
        this.status = JobStatus.QUEUED;
        this.submittedAt = Instant.now();
    }

    /**
     * Returns the job's identifier.
     *
     * @return the identifier, or {@code null} if not yet persisted
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the identifier of the submitting organization.
     *
     * @return the submitting organization's identifier
     */
    public Long getOrganizationId() {
        return organizationId;
    }

    /**
     * Returns the type of resource the job needs.
     *
     * @return the resource type
     */
    public ResourceType getResourceType() {
        return resourceType;
    }

    /**
     * Returns the number of resources the job needs.
     *
     * @return the number of units requested
     */
    public int getUnitsRequested() {
        return unitsRequested;
    }

    /**
     * Returns the minimum memory each resource must have.
     *
     * @return minimum memory in gigabytes
     */
    public int getMinMemoryGb() {
        return minMemoryGb;
    }

    /**
     * Returns the minimum cores each CPU must have.
     *
     * @return the minimum core count, or {@code null} for GPU jobs
     */
    public Integer getMinCores() {
        return minCores;
    }

    /**
     * Returns the maximum lease length.
     *
     * @return maximum duration in hours
     */
    public int getMaxDurationHours() {
        return maxDurationHours;
    }

    /**
     * Returns the job's current lifecycle state.
     *
     * @return the job status
     */
    public JobStatus getStatus() {
        return status;
    }

    /**
     * Sets the job's lifecycle state.
     *
     * @param status the new job status
     */
    public void setStatus(final JobStatus status) {
        this.status = status;
    }

    /**
     * Returns when the job was submitted.
     *
     * @return the submission time
     */
    public Instant getSubmittedAt() {
        return submittedAt;
    }

    /**
     * Returns when the job started running.
     *
     * @return the start time, or {@code null} if it has not started
     */
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Sets when the job started running.
     *
     * @param startedAt the start time
     */
    public void setStartedAt(final Instant startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * Returns when the job's lease ends.
     *
     * @return the lease end, or {@code null} if it has not started
     */
    public Instant getLeaseEnd() {
        return leaseEnd;
    }

    /**
     * Sets when the job's lease ends.
     *
     * @param leaseEnd the lease end time
     */
    public void setLeaseEnd(final Instant leaseEnd) {
        this.leaseEnd = leaseEnd;
    }
}
