package com.resourceallocator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Exercises the add resource, update resource, and job queue endpoints
 * through the full Spring stack against the in-memory test database.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@Transactional
class ResourceAndJobQueueApiTest {

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
    void addResourceWithoutApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cpuBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addResourceWithUnknownApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cpuBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidApiKeyIsRejectedBeforeBodyValidation() throws Exception {
        mockMvc.perform(post("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addResourceCreatesResource() throws Exception {
        mockMvc.perform(post("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cpuBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.resourceType").value("CPU"))
                .andExpect(jsonPath("$.memoryGb").value(32))
                .andExpect(jsonPath("$.cores").value(8))
                .andExpect(jsonPath("$.online").value(true))
                .andExpect(header().string("Location",
                        matchesPattern(".*/resources/\\d+$")));
    }

    @Test
    void addResourceRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\": \"GPU\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void addResourceRejectsCpuWithoutCores() throws Exception {
        mockMvc.perform(post("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\": \"CPU\", "
                                + "\"memoryGb\": 32, \"online\": true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addResourceRejectsUnknownResourceType() throws Exception {
        mockMvc.perform(post("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\": \"TPU\", "
                                + "\"memoryGb\": 32, \"online\": true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateResourceTakesResourceOffline() throws Exception {
        Resource resource = resourceRepository.save(new Resource(
                organization.getId(), ResourceType.GPU, 80, null, true));

        mockMvc.perform(patch("/resources/" + resource.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"online\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resource.getId()))
                .andExpect(jsonPath("$.online").value(false));
    }

    @Test
    void updateResourceHeldByRunningJobIsConflict() throws Exception {
        Resource gpu = resourceRepository.save(new Resource(
                organization.getId(), ResourceType.GPU, 80, null, true));
        Job running = new Job(organization.getId(), ResourceType.GPU, 1, 40,
                null, 1);
        running.setStatus(JobStatus.RUNNING);
        running.setStartedAt(Instant.now());
        running.setLeaseEnd(Instant.now().plusSeconds(3600));
        running = jobRepository.saveAndFlush(running);
        jobRepository.assignResource(running.getId(), gpu.getId());

        mockMvc.perform(patch("/resources/" + gpu.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"online\": false}"))
                .andExpect(status().isConflict());
        assertThat(resourceRepository.findById(gpu.getId()).orElseThrow()
                .isOnline()).isTrue();
    }

    @Test
    void updateResourceHeldByExpiredJobGoesOffline() throws Exception {
        Resource gpu = resourceRepository.save(new Resource(
                organization.getId(), ResourceType.GPU, 80, null, true));
        Job expired = new Job(organization.getId(), ResourceType.GPU, 1, 40,
                null, 1);
        expired.setStatus(JobStatus.RUNNING);
        expired.setStartedAt(Instant.now().minusSeconds(7200));
        expired.setLeaseEnd(Instant.now().minusSeconds(3600));
        expired = jobRepository.saveAndFlush(expired);
        jobRepository.assignResource(expired.getId(), gpu.getId());

        mockMvc.perform(patch("/resources/" + gpu.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"online\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").value(false));
        assertThat(jobRepository.findById(expired.getId()).orElseThrow()
                .getStatus()).isEqualTo(JobStatus.DONE);
    }

    @Test
    void updateResourceCanBringOfflineResourceOnline() throws Exception {
        Resource gpu = resourceRepository.save(new Resource(
                organization.getId(), ResourceType.GPU, 80, null, false));

        mockMvc.perform(patch("/resources/" + gpu.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"online\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").value(true));
    }

    @Test
    void updateResourceOfAnotherOrganizationIsBadRequest() throws Exception {
        Resource foreign = resourceRepository.save(new Resource(
                otherOrganization.getId(), ResourceType.GPU, 80, null, true));

        mockMvc.perform(patch("/resources/" + foreign.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"online\": false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateResourceRequiresOnlineFlag() throws Exception {
        Resource resource = resourceRepository.save(new Resource(
                organization.getId(), ResourceType.GPU, 80, null, true));

        mockMvc.perform(patch("/resources/" + resource.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateResourceWithoutApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/resources/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"online\": false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jobQueueListsOnlyCallersJobs() throws Exception {
        resourceRepository.save(new Resource(
                organization.getId(), ResourceType.GPU, 80, null, true));
        Job job = jobRepository.save(new Job(
                organization.getId(), ResourceType.GPU, 1, 40, null, 2));
        jobRepository.save(new Job(
                otherOrganization.getId(), ResourceType.GPU, 1, 40, null, 2));

        mockMvc.perform(get("/jobs/queue")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs", hasSize(1)))
                .andExpect(jsonPath("$.jobs[0].jobId").value(job.getId()))
                .andExpect(jsonPath("$.jobs[0].status").value("QUEUED"))
                .andExpect(jsonPath("$.jobs[0].queuePosition").value(1))
                .andExpect(jsonPath("$.jobs[0].blocked").value(false))
                .andExpect(jsonPath("$.jobs[0].requiredResources.resourceType")
                        .value("GPU"))
                .andExpect(jsonPath("$.summary.queued").value(1))
                .andExpect(jsonPath("$.summary.running").value(0))
                .andExpect(jsonPath("$.summary.done").value(0));
    }

    @Test
    void jobQueueWithoutApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/jobs/queue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jobQueueRejectsWindowEndingBeforeStart() throws Exception {
        mockMvc.perform(get("/jobs/queue")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .param("start", "2026-10-13T12:00:00Z")
                        .param("end", "2026-10-13T11:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jobQueueAcceptsLowercaseFilters() throws Exception {
        jobRepository.save(new Job(
                organization.getId(), ResourceType.GPU, 1, 40, null, 2));
        jobRepository.save(new Job(
                organization.getId(), ResourceType.CPU, 1, 16, 4, 2));

        mockMvc.perform(get("/jobs/queue")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .param("status", "queued")
                        .param("resourceType", "gpu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs", hasSize(1)))
                .andExpect(jsonPath("$.jobs[0].requiredResources.resourceType")
                        .value("GPU"));
    }

    @Test
    void jobQueueRejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/jobs/queue")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .param("status", "PAUSED"))
                .andExpect(status().isBadRequest());
    }

    private static String cpuBody() {
        return "{\"resourceType\": \"CPU\", \"memoryGb\": 32, "
                + "\"cores\": 8, \"online\": true}";
    }
}
