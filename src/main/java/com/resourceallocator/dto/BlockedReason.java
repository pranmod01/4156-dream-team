package com.resourceallocator.dto;

/**
 * Why a queued job cannot currently be scheduled.
 */
public enum BlockedReason {
    /** Fewer online resources match the job than it requests. */
    NO_MATCHING_RESOURCE,
    /** Starting the job would exceed the organization's usage limit. */
    EXCEEDS_USAGE_LIMIT,
    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    /**
     * Enough online resources match the job, but too few are free because
     * running jobs hold the rest. The job can run once they finish.
     */
    RESOURCES_IN_USE
    // END AI-generated
}
