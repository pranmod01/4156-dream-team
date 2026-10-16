package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.model.Organization;
import java.time.Instant;

/**
 * A newly registered client, including its API key.
 *
 * <p>This is the only response that ever contains the raw API key; the
 * service stores just its hash, so the client must save it now.
 *
 * @param id the organization identifier
 * @param orgName the organization name
 * @param maxCpus the CPU usage limit
 * @param maxGpus the GPU usage limit
 * @param createdAt when the organization was registered
 * @param apiKey the key to send in the {@code X-API-Key} header
 */
public record CreateClientResponse(
        Long id,
        String orgName,
        int maxCpus,
        int maxGpus,
        Instant createdAt,
        String apiKey) {

    /**
     * Builds the response for a newly saved organization.
     *
     * @param organization the saved organization entity
     * @param apiKey the raw API key issued to it
     * @return the response view
     */
    public static CreateClientResponse from(final Organization organization,
            final String apiKey) {
        return new CreateClientResponse(
                organization.getId(),
                organization.getOrgName(),
                organization.getMaxCpus(),
                organization.getMaxGpus(),
                organization.getCreatedAt(),
                apiKey);
    }
}
