package com.resourceallocator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A CPU or GPU owned by an organization that jobs can run on.
 */
@Entity
@Table(name = "resource")
public class Resource {

    /** Database-generated identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifier of the organization that owns this resource. */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    /** Whether this resource is a CPU or a GPU. */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, updatable = false,
            length = 10)
    private ResourceType resourceType;

    /** Memory available on this resource, in gigabytes. */
    @Column(name = "memory_gb", nullable = false)
    private int memoryGb;

    /** Number of cores for a CPU; always {@code null} for a GPU. */
    @Column(name = "cores")
    private Integer cores;

    /** Whether the resource is online and can be scheduled. */
    @Column(name = "is_online", nullable = false)
    private boolean online;

    /**
     * Creates an empty resource. Required by JPA.
     */
    protected Resource() {
        // Required by JPA.
    }

    /**
     * Creates a new resource owned by the given organization.
     *
     * @param organizationId identifier of the owning organization
     * @param resourceType whether the resource is a CPU or a GPU
     * @param memoryGb memory available, in gigabytes
     * @param cores core count for a CPU, or {@code null} for a GPU
     * @param online whether the resource can currently be scheduled
     */
    public Resource(final Long organizationId,
            final ResourceType resourceType, final int memoryGb,
            final Integer cores, final boolean online) {
        this.organizationId = organizationId;
        this.resourceType = resourceType;
        this.memoryGb = memoryGb;
        this.cores = cores;
        this.online = online;
    }

    /**
     * Returns the resource's identifier.
     *
     * @return the identifier, or {@code null} if not yet persisted
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the identifier of the owning organization.
     *
     * @return the owning organization's identifier
     */
    public Long getOrganizationId() {
        return organizationId;
    }

    /**
     * Returns whether this resource is a CPU or a GPU.
     *
     * @return the resource type
     */
    public ResourceType getResourceType() {
        return resourceType;
    }

    /**
     * Returns the memory available on this resource.
     *
     * @return memory in gigabytes
     */
    public int getMemoryGb() {
        return memoryGb;
    }

    /**
     * Returns the core count of this resource.
     *
     * @return the core count, or {@code null} for a GPU
     */
    public Integer getCores() {
        return cores;
    }

    /**
     * Returns whether the resource is online.
     *
     * @return {@code true} if the resource can be scheduled
     */
    public boolean isOnline() {
        return online;
    }

    /**
     * Marks the resource as online or offline.
     *
     * @param online {@code true} to make the resource schedulable
     */
    public void setOnline(final boolean online) {
        this.online = online;
    }
}
