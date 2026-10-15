package com.resourceallocator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A client organization, its hashed API key, and its usage limits.
 */
@Entity
@Table(name = "organization")
public class Organization {

    /** Database-generated identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique display name of the organization. */
    @Column(name = "org_name", nullable = false, unique = true)
    private String orgName;

    /** SHA-256 hex digest of the organization's API key. */
    @Column(name = "api_key_hash", nullable = false, unique = true,
            length = 64)
    private String apiKeyHash;

    /** Maximum number of CPU units the organization may use at once. */
    @Column(name = "max_cpus", nullable = false)
    private int maxCpus;

    /** Maximum number of GPU units the organization may use at once. */
    @Column(name = "max_gpus", nullable = false)
    private int maxGpus;

    /** When the organization was registered. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Creates an empty organization. Required by JPA.
     */
    protected Organization() {
        // Required by JPA.
    }

    /**
     * Creates a new organization registered at the current time.
     *
     * @param orgName unique organization name
     * @param apiKeyHash SHA-256 hex digest of the organization's API key
     * @param maxCpus maximum CPU units the organization may use at once
     * @param maxGpus maximum GPU units the organization may use at once
     */
    public Organization(final String orgName, final String apiKeyHash,
            final int maxCpus, final int maxGpus) {
        this.orgName = orgName;
        this.apiKeyHash = apiKeyHash;
        this.maxCpus = maxCpus;
        this.maxGpus = maxGpus;
        this.createdAt = Instant.now();
    }

    /**
     * Returns the organization's identifier.
     *
     * @return the identifier, or {@code null} if not yet persisted
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the organization's name.
     *
     * @return the organization name
     */
    public String getOrgName() {
        return orgName;
    }

    /**
     * Returns the SHA-256 hex digest of the organization's API key.
     *
     * @return the API key hash
     */
    public String getApiKeyHash() {
        return apiKeyHash;
    }

    /**
     * Returns the maximum number of CPU units usable at once.
     *
     * @return the CPU limit
     */
    public int getMaxCpus() {
        return maxCpus;
    }

    /**
     * Returns the maximum number of GPU units usable at once.
     *
     * @return the GPU limit
     */
    public int getMaxGpus() {
        return maxGpus;
    }

    /**
     * Returns when the organization was registered.
     *
     * @return the registration time
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
