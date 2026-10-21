package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.resourceallocator.model.JobStatus;
import com.resourceallocator.repository.JobRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobDispatchSchedulerTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobDispatcher jobDispatcher;

    @Test
    void dispatchesEveryOrganizationWithUnfinishedJobs() {
        when(jobRepository.findOrganizationIdsWithJobsNotIn(JobStatus.DONE))
                .thenReturn(List.of(1L, 2L));

        new JobDispatchScheduler(jobRepository, jobDispatcher).dispatchAll();

        verify(jobDispatcher).dispatch(1L);
        verify(jobDispatcher).dispatch(2L);
    }

    @Test
    void doesNothingWhenNoJobsAreActive() {
        when(jobRepository.findOrganizationIdsWithJobsNotIn(JobStatus.DONE))
                .thenReturn(List.of());

        new JobDispatchScheduler(jobRepository, jobDispatcher).dispatchAll();

        verifyNoInteractions(jobDispatcher);
    }
}
