package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resourceallocator.dto.AddJobRequest;
import com.resourceallocator.dto.JobResponse;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.exception.NotFoundException;
import com.resourceallocator.model.Job;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private static final long ORG_ID = 3L;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobDispatcher jobDispatcher;

    private JobService service;

    private Organization organization;

    @BeforeEach
    void setUp() {
        service = new JobService(jobRepository, jobDispatcher);
        // Limits: 4 CPUs, 2 GPUs.
        organization = new Organization("acme", "hash", 4, 2);
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
    }

    @Test
    void addJobSavesQueuedJobAndDispatches() {
        when(jobRepository.saveAndFlush(any(Job.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0),
                        7L));
        when(jobRepository.findResourceIdsByJobId(7L)).thenReturn(List.of());

        JobResponse response = service.addJob(organization,
                new AddJobRequest(ResourceType.CPU, 2, 16, 4, 3));

        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(saved.getValue().getUnitsRequested()).isEqualTo(2);
        assertThat(saved.getValue().getMinCores()).isEqualTo(4);
        assertThat(saved.getValue().getMaxDurationHours()).isEqualTo(3);
        verify(jobDispatcher).dispatch(ORG_ID);
        assertThat(response.jobId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void addJobAcceptsRequestAtExactlyTheLimit() {
        when(jobRepository.saveAndFlush(any(Job.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0),
                        7L));

        service.addJob(organization,
                new AddJobRequest(ResourceType.GPU, 2, 40, null, 1));

        verify(jobDispatcher).dispatch(ORG_ID);
    }

    @Test
    void addJobRejectsMoreUnitsThanTheLimit() {
        assertThatThrownBy(() -> service.addJob(organization,
                new AddJobRequest(ResourceType.GPU, 3, 40, null, 1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("limit of 2");
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void addJobRejectsCpuJobWithoutCores() {
        assertThatThrownBy(() -> service.addJob(organization,
                new AddJobRequest(ResourceType.CPU, 1, 16, null, 1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("minCores is required");
    }

    @Test
    void addJobRejectsGpuJobWithCores() {
        assertThatThrownBy(() -> service.addJob(organization,
                new AddJobRequest(ResourceType.GPU, 1, 16, 4, 1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must not be set");
    }

    @Test
    void getJobStatusDispatchesThenReturnsJob() {
        Job job = withId(gpuJob(), 7L);
        when(jobRepository.findByIdAndOrganizationId(7L, ORG_ID))
                .thenReturn(Optional.of(job));
        when(jobRepository.findResourceIdsByJobId(7L))
                .thenReturn(List.of(100L));

        JobResponse response = service.getJobStatus(organization, 7L);

        verify(jobDispatcher).dispatch(ORG_ID);
        assertThat(response.jobId()).isEqualTo(7L);
        assertThat(response.resourceIds()).containsExactly(100L);
    }

    @Test
    void getJobStatusOfUnknownJobIsNotFound() {
        when(jobRepository.findByIdAndOrganizationId(7L, ORG_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJobStatus(organization, 7L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteJobDeletesQueuedJob() {
        Job job = withId(gpuJob(), 7L);
        when(jobRepository.findByIdAndOrganizationId(7L, ORG_ID))
                .thenReturn(Optional.of(job));

        assertThat(service.deleteJob(organization, 7L).deletedJobId())
                .isEqualTo(7L);
        verify(jobRepository).delete(job);
    }

    @Test
    void deleteJobDeletesDoneJob() {
        Job job = withId(gpuJob(), 7L);
        job.setStatus(JobStatus.DONE);
        when(jobRepository.findByIdAndOrganizationId(7L, ORG_ID))
                .thenReturn(Optional.of(job));

        service.deleteJob(organization, 7L);

        verify(jobRepository).delete(job);
    }

    @Test
    void deleteJobRejectsRunningJob() {
        Job job = withId(gpuJob(), 7L);
        job.setStatus(JobStatus.RUNNING);
        when(jobRepository.findByIdAndOrganizationId(7L, ORG_ID))
                .thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.deleteJob(organization, 7L))
                .isInstanceOf(ConflictException.class);
        verify(jobRepository, never()).delete(any());
    }

    @Test
    void deleteJobOfUnknownJobIsNotFound() {
        when(jobRepository.findByIdAndOrganizationId(7L, ORG_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteJob(organization, 7L))
                .isInstanceOf(NotFoundException.class);
    }

    private static Job gpuJob() {
        return new Job(ORG_ID, ResourceType.GPU, 1, 40, null, 2);
    }

    private static Job withId(final Job job, final long id) {
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }
}
