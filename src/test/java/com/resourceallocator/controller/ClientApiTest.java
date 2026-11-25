package com.resourceallocator.controller;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.resourceallocator.auth.ApiKeyAuthInterceptor;
import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.model.Organization;
import com.resourceallocator.repository.OrganizationRepository;
import com.resourceallocator.service.JobDispatcher;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Exercises the {@link ClientController} endpoints through the full Spring
 * stack against the in-memory test database. Each test runs in a
 * transaction that is rolled back afterwards.
 */
@SpringBootTest
@Transactional
class ClientApiTest {

    private static final String API_KEY = "test-api-key";

    /** Longest organization name the API accepts. */
    private static final int MAX_ORG_NAME_LENGTH = 255;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JobDispatcher jobDispatcher;

    private MockMvc mockMvc;

    private Organization organization;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        organization = organizationRepository.save(new Organization(
                "test-org", ApiKeyHasher.hash(API_KEY), 4, 2));
    }

    @Test
    void createClientNeedsNoApiKeyAndReturnsKey() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientBody("new-org")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.orgName").value("new-org"))
                .andExpect(jsonPath("$.maxCpus").value(8))
                .andExpect(jsonPath("$.maxGpus").value(2))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.apiKey").isNotEmpty());
    }

    @Test
    void issuedApiKeyAuthenticatesTheNewClient() throws Exception {
        String body = mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientBody("new-org")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String apiKey = JsonPath.read(body, "$.apiKey");

        mockMvc.perform(get("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources", hasSize(0)));
    }

    @Test
    void createClientRejectsDuplicateName() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientBody("test-org")))
                .andExpect(status().isConflict());
    }

    @Test
    void createClientRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgName\": \"new-org\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void createClientRejectsBlankNameAndNegativeLimits() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgName\": \"  \", "
                                + "\"maxCpus\": -1, \"maxGpus\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    @Test
    void createClientAcceptsNameAtMaxLength() throws Exception {
        String orgName = "a".repeat(MAX_ORG_NAME_LENGTH);
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientBody(orgName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orgName").value(orgName));
    }

    @Test
    void createClientRejectsNameOverMaxLength() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientBody(
                                "a".repeat(MAX_ORG_NAME_LENGTH + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    @Test
    void updateLimitsWithoutApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/clients/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxCpus\": 8}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateLimitsChangesOnlyTheGivenLimit() throws Exception {
        mockMvc.perform(updateLimits("{\"maxCpus\": 8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgName").value("test-org"))
                .andExpect(jsonPath("$.maxCpus").value(8))
                .andExpect(jsonPath("$.maxGpus").value(2))
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        mockMvc.perform(get("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(jsonPath("$.summary.cpu.limit").value(8))
                .andExpect(jsonPath("$.summary.gpu.limit").value(2));
    }

    @Test
    void updateLimitsRejectsEmptyBody() throws Exception {
        mockMvc.perform(updateLimits("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateLimitsRejectsNegativeLimits() throws Exception {
        mockMvc.perform(updateLimits("{\"maxCpus\": -1, \"maxGpus\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void updateLimitsRejectsLimitBelowAQueuedJob() throws Exception {
        // No GPUs are registered, so this 2-unit job stays queued.
        submitGpuJob(2).andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(updateLimits("{\"maxGpus\": 1}"))
                .andExpect(status().isConflict());
        mockMvc.perform(updateLimits("{\"maxGpus\": 2}"))
                .andExpect(status().isOk());
    }

    @Test
    void loweringLimitKeepsRunningJobsAndHoldsNewOnes() throws Exception {
        addGpu();
        addGpu();
        String running = submitGpuJob(2)
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andReturn().getResponse().getContentAsString();
        Integer runningId = JsonPath.read(running, "$.jobId");

        mockMvc.perform(updateLimits("{\"maxGpus\": 1}"))
                .andExpect(status().isOk());

        // The running job holds 2 units, over the new limit of 1, but is
        // not stopped. A new job waits until usage falls below the limit.
        mockMvc.perform(get("/jobs/" + runningId)
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(jsonPath("$.status").value("RUNNING"));
        String queued = submitGpuJob(1)
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        Integer queuedId = JsonPath.read(queued, "$.jobId");
        mockMvc.perform(get("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(jsonPath("$.summary.gpu.limit").value(1))
                .andExpect(jsonPath("$.summary.gpu.inUse").value(2))
                .andExpect(jsonPath("$.summary.gpu.remaining").value(0));
        mockMvc.perform(get("/jobs/queue")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .param("status", "queued"))
                .andExpect(jsonPath("$.jobs[0].blockedReason")
                        .value("EXCEEDS_USAGE_LIMIT"));

        // Once the running job's lease ends, the new limit applies.
        jobDispatcher.dispatch(organization.getId(),
                Instant.now().plus(Duration.ofHours(2)));
        mockMvc.perform(get("/jobs/" + runningId)
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(jsonPath("$.status").value("DONE"));
        mockMvc.perform(get("/jobs/" + queuedId)
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void raisingLimitStartsWaitingJobsAtOnce() throws Exception {
        addGpu();
        addGpu();
        addGpu();
        submitGpuJob(2).andExpect(jsonPath("$.status").value("RUNNING"));
        String queued = submitGpuJob(1)
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        Integer queuedId = JsonPath.read(queued, "$.jobId");

        mockMvc.perform(updateLimits("{\"maxGpus\": 3}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/jobs/" + queuedId)
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void submittingJobUsesTheUpdatedLimit() throws Exception {
        mockMvc.perform(updateLimits("{\"maxGpus\": 1}"))
                .andExpect(status().isOk());

        submitGpuJob(2).andExpect(status().isBadRequest());
    }

    private static RequestBuilder updateLimits(final String body) {
        return patch("/clients/me")
                .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void addGpu() throws Exception {
        mockMvc.perform(post("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\": \"GPU\", "
                                + "\"memoryGb\": 40, \"online\": true}"))
                .andExpect(status().isCreated());
    }

    private ResultActions submitGpuJob(
            final int units) throws Exception {
        return mockMvc.perform(post("/jobs")
                .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceType\": \"GPU\", \"unitsRequested\": "
                        + units + ", \"minMemoryGb\": 16, "
                        + "\"maxDurationHours\": 1}"));
    }
    // END AI-generated

    private static String clientBody(final String orgName) {
        return "{\"orgName\": \"" + orgName + "\", "
                + "\"maxCpus\": 8, \"maxGpus\": 2}";
    }
}
