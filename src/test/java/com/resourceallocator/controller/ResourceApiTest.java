package com.resourceallocator.controller;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resourceallocator.auth.ApiKeyAuthInterceptor;
import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.Resource;
import com.resourceallocator.model.ResourceType;
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
 * Exercises the {@link ResourceController} list endpoint through the full
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
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(cpu.getId()))
                .andExpect(jsonPath("$[0].resourceType").value("CPU"))
                .andExpect(jsonPath("$[0].cores").value(8))
                .andExpect(jsonPath("$[0].online").value(true))
                .andExpect(jsonPath("$[1].id").value(gpu.getId()))
                .andExpect(jsonPath("$[1].resourceType").value("GPU"))
                .andExpect(jsonPath("$[1].online").value(false));
    }

    @Test
    void listResourcesWithoutApiKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/resources"))
                .andExpect(status().isUnauthorized());
    }
}
