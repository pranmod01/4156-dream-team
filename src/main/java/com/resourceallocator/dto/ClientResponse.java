package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.model.Organization;
import java.time.Instant;

/**
 * A registered client and its usage limits. Unlike
 * {@link CreateClientResponse}, it never contains the API key.
 *
 * @param id the organization identifier
 * @param orgName the organization name
 * @param maxCpus the CPU usage limit
 * @param maxGpus the GPU usage limit
 * @param createdAt when the organization was registered
 */
public record ClientResponse(
        Long id,
        String orgName,
        int maxCpus,
        int maxGpus,
        Instant createdAt) {

    /**
     * Builds the response for an organization.
     *
     * @param organization the organization entity
     * @return the response view
     */
    public static ClientResponse from(final Organization organization) {
        return new ClientResponse(
                organization.getId(),
                organization.getOrgName(),
                organization.getMaxCpus(),
                organization.getMaxGpus(),
                organization.getCreatedAt());
    }
}
