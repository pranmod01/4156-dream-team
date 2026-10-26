package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

/**
 * An organization's usage against its limits, per resource type.
 *
 * @param cpu CPU usage
 * @param gpu GPU usage
 */
public record ResourceSummary(ResourceUsage cpu, ResourceUsage gpu) {
}
