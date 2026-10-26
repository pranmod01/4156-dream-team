package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

/**
 * An organization's usage of one resource type against its limit.
 *
 * @param limit most units the organization's running jobs may hold
 * @param inUse units held by the organization's running jobs
 * @param remaining units the organization may still start
 *                  ({@code limit - inUse}, never negative)
 * @param freeResources online resources not held by any running job;
 *                      may be more than {@code remaining}, since the
 *                      limit can be stricter than the hardware
 */
public record ResourceUsage(
        int limit,
        int inUse,
        int remaining,
        long freeResources) {
}
