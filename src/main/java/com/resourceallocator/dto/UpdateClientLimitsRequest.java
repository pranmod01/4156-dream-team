package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of a request to change an organization's usage limits. Either
 * field may be left out to keep its current value, but at least one must
 * be given.
 *
 * @param maxCpus new CPU usage limit, or {@code null} to keep the current
 *                one
 * @param maxGpus new GPU usage limit, or {@code null} to keep the current
 *                one
 */
public record UpdateClientLimitsRequest(
        @PositiveOrZero Integer maxCpus,
        @PositiveOrZero Integer maxGpus) {
}
