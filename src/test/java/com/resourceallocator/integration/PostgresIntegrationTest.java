package com.resourceallocator.integration;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for integration tests. Starts the whole application on a
 * random port against a real PostgreSQL database running in Docker, so
 * the tests exercise the real Flyway migrations, PostgreSQL's constraint
 * checks and row locking, and the full HTTP stack, with nothing mocked.
 *
 * <p>The container is started once and shared by every integration test
 * class (the "singleton container" pattern), so the Spring context is
 * also cached across classes. Each test starts from empty tables.
 *
 * <p>Tests are not {@code @Transactional}: every request commits, as in
 * production, which is what the concurrency tests rely on. The background
 * dispatch scheduler is pushed out to an hour so it cannot interfere;
 * tests that need time to pass call {@code JobDispatcher.dispatch} with
 * a future timestamp instead.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "dispatcher.interval-ms=3600000")
abstract class PostgresIntegrationTest {

    /** Header that carries the caller's API key. */
    protected static final String API_KEY_HEADER = "X-API-Key";

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbc;

    private RestClient http;

    @DynamicPropertySource
    static void postgresProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void resetDatabaseAndClient() {
        jdbc.execute("TRUNCATE job_resource, job, resource, organization "
                + "RESTART IDENTITY CASCADE");
        // The JDK client supports PATCH; the default one does not.
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    /**
     * Sends a request and returns the response, whatever its status.
     *
     * @param method HTTP method
     * @param path path and query string
     * @param apiKey API key to send, or {@code null} to send none
     * @param jsonBody request body, or {@code null} for none
     * @return the status, body and Location header
     */
    protected ApiResponse call(final HttpMethod method, final String path,
            final String apiKey, final String jsonBody) {
        RestClient.RequestBodySpec request = http.method(method).uri(path);
        if (apiKey != null) {
            request.header(API_KEY_HEADER, apiKey);
        }
        if (jsonBody != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(jsonBody);
        }
        return request.exchange((req, res) -> new ApiResponse(
                res.getStatusCode().value(),
                new String(readBody(res.getBody()), StandardCharsets.UTF_8),
                res.getHeaders().getLocation()));
    }

    /**
     * Registers an organization through {@code POST /clients}.
     *
     * @param orgName organization name
     * @param maxCpus CPU usage limit
     * @param maxGpus GPU usage limit
     * @return the new organization's id and raw API key
     */
    protected Client createClient(final String orgName, final int maxCpus,
            final int maxGpus) {
        ApiResponse response = call(HttpMethod.POST, "/clients", null,
                "{\"orgName\": \"" + orgName + "\", \"maxCpus\": " + maxCpus
                        + ", \"maxGpus\": " + maxGpus + "}");
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return new Client(response.longAt("$.id"),
                response.stringAt("$.apiKey"));
    }

    /**
     * Adds an online GPU through {@code POST /resources}.
     *
     * @param apiKey owner's API key
     * @param memoryGb GPU memory
     * @return the new resource's id
     */
    protected long addGpu(final String apiKey, final int memoryGb) {
        return addResource(apiKey, "{\"resourceType\": \"GPU\", "
                + "\"memoryGb\": " + memoryGb + ", \"online\": true}");
    }

    /**
     * Adds an online CPU through {@code POST /resources}.
     *
     * @param apiKey owner's API key
     * @param memoryGb memory
     * @param cores core count
     * @return the new resource's id
     */
    protected long addCpu(final String apiKey, final int memoryGb,
            final int cores) {
        return addResource(apiKey, "{\"resourceType\": \"CPU\", "
                + "\"memoryGb\": " + memoryGb + ", \"cores\": " + cores
                + ", \"online\": true}");
    }

    private long addResource(final String apiKey, final String body) {
        ApiResponse response =
                call(HttpMethod.POST, "/resources", apiKey, body);
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return response.longAt("$.id");
    }

    /**
     * Submits a GPU job through {@code POST /jobs}.
     *
     * @param apiKey submitter's API key
     * @param units GPUs requested
     * @param minMemoryGb minimum memory per GPU
     * @return the 201 response
     */
    protected ApiResponse submitGpuJob(final String apiKey, final int units,
            final int minMemoryGb) {
        return call(HttpMethod.POST, "/jobs", apiKey,
                "{\"resourceType\": \"GPU\", \"unitsRequested\": " + units
                        + ", \"minMemoryGb\": " + minMemoryGb
                        + ", \"maxDurationHours\": 2}");
    }

    private static byte[] readBody(final java.io.InputStream body) {
        try {
            return body.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * An organization created through the API.
     *
     * @param id organization id
     * @param apiKey raw API key
     */
    protected record Client(long id, String apiKey) {
    }

    /**
     * A response from the running application.
     *
     * @param status HTTP status code
     * @param body response body
     * @param location Location header, if any
     */
    protected record ApiResponse(int status, String body, URI location) {

        /**
         * Reads a value from the JSON body.
         *
         * @param path JsonPath expression
         * @param <T> value type
         * @return the value
         */
        <T> T at(final String path) {
            return JsonPath.read(body, path);
        }

        long longAt(final String path) {
            return ((Number) JsonPath.read(body, path)).longValue();
        }

        String stringAt(final String path) {
            return JsonPath.read(body, path);
        }
    }
}
