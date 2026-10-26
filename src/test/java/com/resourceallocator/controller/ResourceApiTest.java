package com.resourceallocator.controller;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resourceallocator.auth.ApiKeyAuthInterceptor;
import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.model.Job;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.OrganizationRepository;
import com.resourceallocator.repository.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Exercises the {@link ResourceController} status endpoint through the full
 * Spring stack against the in-memory test database. Each test runs in a
 * transaction that is rolled back afterwards.
 */
@SpringBootTest
@Transactional
class ResourceApiTest {

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
    void listResourcesReturnsOnlyCallersResourcesIncludingOffline()
            throws Exception {
        Resource cpu = resourceRepository.save(new Resource(
                organization.getId(), ResourceType.CPU, 32, 8, true));
        Resource gpu = resourceRepository.save(new Resource(
                organization.getId(), ResourceType.GPU, 80, null, false));
        resourceRepository.save(new Resource(
                otherOrganization.getId(), ResourceType.GPU, 80, null, true));

        mockMvc.perform(get("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.resources[0].id").value(cpu.getId()))
                .andExpect(jsonPath("$.resources[0].resourceType")
                        .value("CPU"))
                .andExpect(jsonPath("$.resources[0].cores").value(8))
                .andExpect(jsonPath("$.resources[0].online").value(true))
                .andExpect(jsonPath("$.resources[0].status")
                        .value("AVAILABLE"))
                .andExpect(jsonPath("$.resources[1].id").value(gpu.getId()))
                .andExpect(jsonPath("$.resources[1].resourceType")
                        .value("GPU"))
                .andExpect(jsonPath("$.resources[1].online").value(false))
                .andExpect(jsonPath("$.resources[1].status")
                        .value("OFFLINE"));
    }

    @Test
    void listResourcesShowsResourceHeldByJobTheDispatcherStarts()
            throws Exception {
        Resource cpu = resourceRepository.save(new Resource(
                organization.getId(), ResourceType.CPU, 32, 8, true));
        Job job = jobRepository.save(new Job(
                organization.getId(), ResourceType.CPU, 1, 16, 4, 2));

        mockMvc.perform(get("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources[0].id").value(cpu.getId()))
                .andExpect(jsonPath("$.resources[0].status").value("IN_USE"))
                .andExpect(jsonPath("$.resources[0].runningJobId")
                        .value(job.getId()))
                .andExpect(jsonPath("$.summary.cpu.limit").value(4))
                .andExpect(jsonPath("$.summary.cpu.inUse").value(1))
                .andExpect(jsonPath("$.summary.cpu.remaining").value(3))
                .andExpect(jsonPath("$.summary.cpu.freeResources").value(0))
                .andExpect(jsonPath("$.summary.gpu.limit").value(2))
                .andExpect(jsonPath("$.summary.gpu.inUse").value(0));
    }

    @Test
    void listResourcesAppliesCaseInsensitiveTypeAndMemoryFilters()
            throws Exception {
        resourceRepository.save(new Resource(
                organization.getId(), ResourceType.CPU, 128, 8, true));
        resourceRepository.save(new Resource(
                organization.getId(), ResourceType.GPU, 40, null, true));
        Resource bigGpu = resourceRepository.save(new Resource(
                organization.getId(), ResourceType.GPU, 80, null, true));

        mockMvc.perform(get("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .param("resourceType", "gpu")
                        .param("minMemoryGb", "64"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id")
                        .value(bigGpu.getId()))
                .andExpect(jsonPath("$.summary.cpu.freeResources").value(1));
    }

    @Test
    void listResourcesById() throws Exception {
        resourceRepository.save(new Resource(
                organization.getId(), ResourceType.CPU, 32, 8, true));
        Resource gpu = resourceRepository.save(new Resource(
                organization.getId(), ResourceType.GPU, 80, null, true));

        mockMvc.perform(get("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .param("id", gpu.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(gpu.getId()));
    }

    @Test
    void listResourcesByAnotherOrganizationsIdIsNotFound() throws Exception {
        Resource foreign = resourceRepository.save(new Resource(
                otherOrganization.getId(), ResourceType.GPU, 80, null, true));

        mockMvc.perform(get("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .param("id", foreign.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listResourcesRejectsInvalidFilters() throws Exception {
        mockMvc.perform(get("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .param("resourceType", "tpu"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/resources")
                        .header(ApiKeyAuthInterceptor.API_KEY_HEADER, API_KEY)
                        .param("minCores", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listResourcesWithoutApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/resources"))
                .andExpect(status().isUnauthorized());
    }
}
