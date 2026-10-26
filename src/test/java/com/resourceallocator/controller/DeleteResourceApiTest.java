// AI-generated (Claude, Claude Opus 5.5), reviewed by Rob
// AI-edited (Claude, Claude Opus 5.5): GET /resources response shape
package com.resourceallocator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resourceallocator.auth.ApiKeyAuthInterceptor;
import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.model.Job;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.OrganizationRepository;
import com.resourceallocator.repository.ResourceRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Exercises the delete resource endpoint through the full Spring stack
 * against the in-memory test database. Each test runs in a transaction
 * that is rolled back afterwards.
 */
@SpringBootTest
@Transactional
class DeleteResourceApiTest {

    private static final String API_KEY = "test-api-key";

    private static final String OTHER_API_KEY = "other-api-key";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private JobRepository jobRepository;

    private MockMvc mockMvc;

    private Organization organization;

    private Organization otherOrganization;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        organization = organizationRepository.save(new Organization(
                "test-org", ApiKeyHasher.hash(API_KEY), 4, 2));
        otherOrganization = organizationRepository.save(new Organization(
                "other-org", ApiKeyHasher.hash(OTHER_API_KEY), 4, 2));
    }

    @Test
    void deleteFreeResourceReturnsIdAndRemovesIt() throws Exception {
        Resource gpu = gpu(organization, true);

        mockMvc.perform(delete("/resources/" + gpu.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedResourceId").value(gpu.getId()));

        assertThat(resourceRepository.findById(gpu.getId())).isEmpty();
    }

    @Test
    void deleteOfflineResourceSucceeds() throws Exception {
        Resource gpu = gpu(organization, false);

        mockMvc.perform(delete("/resources/" + gpu.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void deletedResourceIsNoLongerListed() throws Exception {
        Resource kept = gpu(organization, true);
        Resource deleted = gpu(organization, true);

        mockMvc.perform(delete("/resources/" + deleted.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(kept.getId()));
    }

    @Test
    void deleteResourceUsedByFinishedJobRemovesItsAssignments()
            throws Exception {
        Resource gpu = gpu(organization, true);
        Job done = job(JobStatus.DONE, null);
        jobRepository.assignResource(done.getId(), gpu.getId());

        mockMvc.perform(delete("/resources/" + gpu.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk());

        // Push the pending delete to the database, which cascades it to
        // job_resource (V2 migration), before reading the table directly.
        resourceRepository.flush();
        assertThat(jobRepository.findResourceIdsByJobId(done.getId()))
                .isEmpty();
        assertThat(jobRepository.findById(done.getId())).isPresent();
    }

    @Test
    void deleteResourceHeldByRunningJobIsConflict() throws Exception {
        Resource gpu = gpu(organization, true);
        Job running = job(JobStatus.RUNNING,
                Instant.now().plusSeconds(3600));
        jobRepository.assignResource(running.getId(), gpu.getId());

        mockMvc.perform(delete("/resources/" + gpu.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isConflict());

        assertThat(resourceRepository.findById(gpu.getId())).isPresent();
    }

    @Test
    void deleteResourceWhoseJobLeaseEndedSucceeds() throws Exception {
        Resource gpu = gpu(organization, true);
        Job expired = job(JobStatus.RUNNING,
                Instant.now().minusSeconds(3600));
        jobRepository.assignResource(expired.getId(), gpu.getId());

        mockMvc.perform(delete("/resources/" + gpu.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk());

        assertThat(jobRepository.findById(expired.getId()).orElseThrow()
                .getStatus()).isEqualTo(JobStatus.DONE);
    }

    @Test
    void deleteOtherOrganizationsResourceIsNotFound() throws Exception {
        Resource foreign = gpu(otherOrganization, true);

        mockMvc.perform(delete("/resources/" + foreign.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isNotFound());

        assertThat(resourceRepository.findById(foreign.getId())).isPresent();
    }

    @Test
    void deleteUnknownResourceIsNotFound() throws Exception {
        mockMvc.perform(delete("/resources/999999")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteResourceWithNonNumericIdIsBadRequest() throws Exception {
        mockMvc.perform(delete("/resources/abc")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteResourceWithoutApiKeyIsUnauthorized() throws Exception {
        Resource gpu = gpu(organization, true);

        mockMvc.perform(delete("/resources/" + gpu.getId()))
                .andExpect(status().isUnauthorized());

        assertThat(resourceRepository.findById(gpu.getId())).isPresent();
    }

    private Resource gpu(final Organization owner, final boolean online) {
        return resourceRepository.saveAndFlush(new Resource(
                owner.getId(), ResourceType.GPU, 80, null, online));
    }

    // Saves one of the organization's GPU jobs in the given state. A
    // started job gets a lease ending at leaseEnd.
    private Job job(final JobStatus status, final Instant leaseEnd) {
        Job job = new Job(organization.getId(), ResourceType.GPU, 1, 40,
                null, 1);
        job.setStatus(status);
        if (leaseEnd != null) {
            job.setStartedAt(leaseEnd.minusSeconds(3600));
            job.setLeaseEnd(leaseEnd);
        }
        return jobRepository.saveAndFlush(job);
    }
}
