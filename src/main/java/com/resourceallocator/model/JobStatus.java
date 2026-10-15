package com.resourceallocator.model;

/**
 * Lifecycle state of a job.
 */
public enum JobStatus {
    /** Submitted and waiting for resources. */
    QUEUED,
    /** Holding resources and executing within its lease. */
    RUNNING,
    /** Finished; its resources have been released. */
    DONE
}
