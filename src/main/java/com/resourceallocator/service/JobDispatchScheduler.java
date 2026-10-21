package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.model.JobStatus;
import com.resourceallocator.repository.JobRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the {@link JobDispatcher} periodically for every organization with
 * queued or running jobs, so leases end and waiting jobs start even when
 * no client calls the API. The interval is set by
 * {@code dispatcher.interval-ms} (default 30 seconds).
 */
@Component
public class JobDispatchScheduler {

    /** Finds organizations with unfinished jobs. */
    private final JobRepository jobRepository;

    /** Dispatches one organization's jobs. */
    private final JobDispatcher jobDispatcher;

    /**
     * Creates the scheduler.
     *
     * @param jobRepository repository used to find active organizations
     * @param jobDispatcher dispatcher run for each organization
     */
    public JobDispatchScheduler(final JobRepository jobRepository,
            final JobDispatcher jobDispatcher) {
        this.jobRepository = jobRepository;
        this.jobDispatcher = jobDispatcher;
    }

    /**
     * Dispatches each organization's jobs in its own transaction.
     */
    @Scheduled(fixedDelayString = "${dispatcher.interval-ms:30000}",
            initialDelayString = "${dispatcher.interval-ms:30000}")
    public void dispatchAll() {
        for (Long organizationId : jobRepository
                .findOrganizationIdsWithJobsNotIn(JobStatus.DONE)) {
            jobDispatcher.dispatch(organizationId);
        }
    }
}
