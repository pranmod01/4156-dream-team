package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of a request to register a new client organization.
 *
 * <p>The 255-character limit on {@code orgName} is checked here, on the
 * raw value, before {@code ClientService} trims it. A name padded with
 * surrounding whitespace past 255 characters is therefore rejected even if
 * its trimmed form would fit.
 *
 * @param orgName unique name of the organization
 * @param maxCpus most CPU units the organization's jobs may hold at once
 * @param maxGpus most GPU units the organization's jobs may hold at once
 */
public record CreateClientRequest(
        @NotBlank @Size(max = 255) String orgName,
        @NotNull @PositiveOrZero Integer maxCpus,
        @NotNull @PositiveOrZero Integer maxGpus) {
}
