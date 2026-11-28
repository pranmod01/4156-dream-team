package com.resourceallocator.dto;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.ResourceType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks the defensive copies added for SpotBugs' EI_EXPOSE_REP and
 * EI_EXPOSE_REP2 findings: a response keeps its own unmodifiable copy of
 * the list it was built from.
 */
class ResponseListCopyTest {

    @Test
    void jobResponseCopiesResourceIds() {
        List<Long> ids = new ArrayList<>(List.of(1L, 2L));
        JobResponse response = new JobResponse(7L, JobStatus.RUNNING,
                ResourceType.CPU, 2, Instant.EPOCH, Instant.EPOCH,
                Instant.EPOCH, ids);

        ids.add(3L);

        assertThat(response.resourceIds()).containsExactly(1L, 2L);
        assertThatThrownBy(() -> response.resourceIds().add(4L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void jobQueueResponseCopiesJobs() {
        List<JobQueueEntry> jobs = new ArrayList<>(List.of(queueEntry(1L)));
        JobQueueResponse response = new JobQueueResponse(jobs,
                new JobStatusCounts(1, 0, 0));

        jobs.add(queueEntry(2L));

        assertThat(response.jobs()).extracting(JobQueueEntry::jobId)
                .containsExactly(1L);
        assertThatThrownBy(() -> response.jobs().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resourceListResponseCopiesResources() {
        List<ResourceStatusEntry> resources =
                new ArrayList<>(List.of(resourceEntry(1L)));
        ResourceListResponse response =
                new ResourceListResponse(resources, null);

        resources.add(resourceEntry(2L));

        assertThat(response.resources()).extracting(ResourceStatusEntry::id)
                .containsExactly(1L);
        assertThatThrownBy(() -> response.resources().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static JobQueueEntry queueEntry(final Long jobId) {
        return new JobQueueEntry(jobId, JobStatus.QUEUED, null,
                Instant.EPOCH, null, null, 1, false, null);
    }

    private static ResourceStatusEntry resourceEntry(final Long id) {
        return new ResourceStatusEntry(id, ResourceType.GPU, 16, null, true,
                null, null);
    }
}
