package com.resourceallocator.controller;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Exercises the add job, get job status, and delete job endpoints through
 * the full Spring stack against the in-memory test database. Each test
 * runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@Transactional
class JobApiTest {

    private static final String API_KEY = "job-api-key";

    private static final String OTHER_API_KEY = "other-job-api-key";

    private static final String GPU_JOB =
            "{\"resourceType\": \"GPU\", \"unitsRequested\": 1, "
                    + "\"minMemoryGb\": 40, \"maxDurationHours\": 2}";

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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        // Limits: 4 CPUs, 2 GPUs.
        organization = organizationRepository.save(new Organization(
                "job-org", ApiKeyHasher.hash(API_KEY), 4, 2));
        organizationRepository.save(new Organization(
                "other-job-org", ApiKeyHasher.hash(OTHER_API_KEY), 4, 2));
    }

    @Test
    void addJobWithoutApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post("/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GPU_JOB))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addJobStartsAtOnceWhenAResourceIsFree() throws Exception {
        Resource gpu = gpu(80);

        addJob(API_KEY, GPU_JOB)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        matchesPattern(".*/jobs/\\d+$")))
                .andExpect(jsonPath("$.jobId").isNumber())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.resourceType").value("GPU"))
                .andExpect(jsonPath("$.unitsRequested").value(1))
                .andExpect(jsonPath("$.startedAt", notNullValue()))
                .andExpect(jsonPath("$.leaseEnd", notNullValue()))
                .andExpect(jsonPath("$.resourceIds",
                        contains(gpu.getId().intValue())));
    }

    @Test
    void addJobStaysQueuedWhenNoResourceIsFree() throws Exception {
        gpu(80);
        addJob(API_KEY, GPU_JOB).andExpect(status().isCreated());

        addJob(API_KEY, GPU_JOB)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.resourceIds", empty()));
    }

    @Test
    void addJobDoesNotUseOfflineOrOtherOrganizationsResources()
            throws Exception {
        resourceRepository.save(new Resource(organization.getId(),
                ResourceType.GPU, 80, null, false));
        Organization other = organizationRepository
                .findByApiKeyHash(ApiKeyHasher.hash(OTHER_API_KEY))
                .orElseThrow();
        resourceRepository.save(new Resource(other.getId(),
                ResourceType.GPU, 80, null, true));

        addJob(API_KEY, GPU_JOB)
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void addCpuJobStartsOnCpuWithEnoughCores() throws Exception {
        resourceRepository.save(new Resource(organization.getId(),
                ResourceType.CPU, 64, 16, true));

        addJob(API_KEY, "{\"resourceType\": \"CPU\", \"unitsRequested\": 1, "
                + "\"minMemoryGb\": 32, \"minCores\": 8, "
                + "\"maxDurationHours\": 1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void addJobRejectsMissingFields() throws Exception {
        addJob(API_KEY, "{\"resourceType\": \"GPU\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    @Test
    void addJobRejectsNonPositiveValues() throws Exception {
        addJob(API_KEY, "{\"resourceType\": \"GPU\", \"unitsRequested\": 0, "
                + "\"minMemoryGb\": -1, \"maxDurationHours\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    @Test
    void addJobRejectsCpuJobWithoutCores() throws Exception {
        addJob(API_KEY, "{\"resourceType\": \"CPU\", \"unitsRequested\": 1, "
                + "\"minMemoryGb\": 32, \"maxDurationHours\": 1}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void addJobRejectsRequestOverTheUsageLimit() throws Exception {
        addJob(API_KEY, "{\"resourceType\": \"GPU\", \"unitsRequested\": 3, "
                + "\"minMemoryGb\": 40, \"maxDurationHours\": 1}")
                .andExpect(status().isBadRequest());
        assertThat(jobRepository.findAll()).isEmpty();
    }

    @Test
    void getJobStatusReturnsTheJob() throws Exception {
        long jobId = createdJobId(GPU_JOB);

        mockMvc.perform(get("/jobs/" + jobId)
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void getJobStatusWithoutApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/jobs/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getJobStatusOfUnknownJobIsNotFound() throws Exception {
        mockMvc.perform(get("/jobs/999999")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJobStatusHidesOtherOrganizationsJobs() throws Exception {
        long jobId = createdJobId(GPU_JOB);

        mockMvc.perform(get("/jobs/" + jobId)
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER,
                                OTHER_API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void jobQueueEndpointIsNotShadowedByJobIdPath() throws Exception {
        mockMvc.perform(get("/jobs/queue")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs").isArray());
    }

    @Test
    void expiredLeaseIsReportedDoneAndFreesTheResource() throws Exception {
        Resource gpu = gpu(80);
        Job expired = new Job(organization.getId(), ResourceType.GPU, 1, 40,
                null, 1);
        expired.setStatus(JobStatus.RUNNING);
        expired.setStartedAt(Instant.now().minusSeconds(7200));
        expired.setLeaseEnd(Instant.now().minusSeconds(3600));
        expired = jobRepository.saveAndFlush(expired);
        jobRepository.assignResource(expired.getId(), gpu.getId());

        long waitingId = createdJobId(GPU_JOB);

        mockMvc.perform(get("/jobs/" + expired.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(jsonPath("$.status").value("DONE"));
        mockMvc.perform(get("/jobs/" + waitingId)
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.resourceIds",
                        contains(gpu.getId().intValue())));
    }

    @Test
    void deleteJobRemovesQueuedJob() throws Exception {
        long jobId = createdJobId(GPU_JOB);

        mockMvc.perform(delete("/jobs/" + jobId)
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedJobId").value(jobId));
        assertThat(jobRepository.findById(jobId)).isEmpty();
    }

    @Test
    void deleteJobRemovesDoneJobAndItsAssignments() throws Exception {
        Resource gpu = gpu(80);
        Job done = new Job(organization.getId(), ResourceType.GPU, 1, 40,
                null, 1);
        done.setStatus(JobStatus.DONE);
        done = jobRepository.saveAndFlush(done);
        jobRepository.assignResource(done.getId(), gpu.getId());

        mockMvc.perform(delete("/jobs/" + done.getId())
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk());
        jobRepository.flush();
        assertThat(jobRepository.findResourceIdsByJobId(done.getId()))
                .isEmpty();
    }

    @Test
    void deleteRunningJobIsConflict() throws Exception {
        gpu(80);
        long jobId = createdJobId(GPU_JOB);

        mockMvc.perform(delete("/jobs/" + jobId)
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isConflict());
        assertThat(jobRepository.findById(jobId)).isPresent();
    }

    @Test
    void deleteUnknownJobIsNotFound() throws Exception {
        mockMvc.perform(delete("/jobs/999999")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteOtherOrganizationsJobIsNotFound() throws Exception {
        long jobId = createdJobId(GPU_JOB);

        mockMvc.perform(delete("/jobs/" + jobId)
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER,
                                OTHER_API_KEY))
                .andExpect(status().isNotFound());
        assertThat(jobRepository.findById(jobId)).isPresent();
    }

    @Test
    void deleteJobWithoutApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/jobs/1"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions addJob(final String apiKey, final String body)
            throws Exception {
        return mockMvc.perform(post("/jobs")
                .header(ApiKeyAuthInterceptor.API_KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long createdJobId(final String body) throws Exception {
        String json = addJob(API_KEY, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll(".*\"jobId\":(\\d+).*", "$1"));
    }

    private Resource gpu(final int memoryGb) {
        return resourceRepository.save(new Resource(organization.getId(),
                ResourceType.GPU, memoryGb, null, true));
    }
}
