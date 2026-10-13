package com.resourceallocator.dto;

/**
 * Why a queued job cannot currently be scheduled.
 */
public enum BlockedReason {
    /** Fewer online resources match the job than it requests. */
    NO_MATCHING_RESOURCE,
    /** Starting the job would exceed the organization's usage limit. */
    EXCEEDS_USAGE_LIMIT
}
