package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

/**
 * Current state of a resource.
 */
public enum ResourceStatus {
    /** Online and not held by any running job. */
    AVAILABLE,
    /** Held by a running job. */
    IN_USE,
    /** Taken offline by its organization; no job can be assigned to it. */
    OFFLINE
}
