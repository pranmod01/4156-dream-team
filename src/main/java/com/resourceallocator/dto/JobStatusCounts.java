package com.resourceallocator.dto;

/**
 * Number of an organization's jobs in each state.
 *
 * @param queued number of queued jobs
 * @param running number of running jobs
 * @param done number of finished jobs
 */
public record JobStatusCounts(long queued, long running, long done) {
}
