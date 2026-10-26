package com.resourceallocator.controller;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.resourceallocator.auth.ApiKeyAuthInterceptor;
import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.model.Organization;
import com.resourceallocator.repository.OrganizationRepository;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        organizationRepository.save(new Organization(
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
                .andExpect(jsonPath("$", hasSize(0)));
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

    private static String clientBody(final String orgName) {
        return "{\"orgName\": \"" + orgName + "\", "
                + "\"maxCpus\": 8, \"maxGpus\": 2}";
    }
}
