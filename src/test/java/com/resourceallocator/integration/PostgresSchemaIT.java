package com.resourceallocator.integration;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resourceallocator.service.JobDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;

/**
 * External integration with PostgreSQL: the real Flyway migrations, the
 * schema's constraints and the V2 cascade, which the H2-based API tests
 * only approximate.
 */
class PostgresSchemaIT extends PostgresIntegrationTest {

    @Autowired
    private JobDispatcher jobDispatcher;

    @Test
    void runsAgainstRealPostgres() {
        String version = jdbc.queryForObject("SELECT version()",
                String.class);

        assertThat(version).startsWith("PostgreSQL 16");
    }

    @Test
    void appliesEveryMigrationSuccessfully() {
        List<Map<String, Object>> migrations = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history "
                        + "WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(migrations).extracting(row -> row.get("version"))
                .containsExactly("1", "2");
        assertThat(migrations).extracting(row -> row.get("success"))
                .containsOnly(true);
    }

    @Test
    void databaseRejectsRowsThatBreakCheckConstraints() {
        jdbc.update("INSERT INTO organization (org_name, api_key_hash, "
                + "max_cpus, max_gpus) VALUES ('acme', 'hash', 4, 4)");
        Long orgId = jdbc.queryForObject(
                "SELECT id FROM organization", Long.class);

        // Memory must be positive.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO resource "
                + "(organization_id, resource_type, memory_gb, is_online) "
                + "VALUES (?, 'GPU', 0, true)", orgId))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A CPU must have cores, and a GPU must not.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO resource "
                + "(organization_id, resource_type, memory_gb, is_online) "
                + "VALUES (?, 'CPU', 16, true)", orgId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO resource "
                + "(organization_id, resource_type, memory_gb, cores, "
                + "is_online) VALUES (?, 'GPU', 16, 4, true)", orgId))
                .isInstanceOf(DataIntegrityViolationException.class);
        // The organization must exist.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO resource "
                + "(organization_id, resource_type, memory_gb, is_online) "
                + "VALUES (9999, 'GPU', 16, true)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingResourceUsedByFinishedJobCascadesItsAssignments() {
        Client client = createClient("acme", 0, 1);
        long gpuId = addGpu(client.apiKey(), 80);
        long jobId = submitGpuJob(client.apiKey(), 1, 40).longAt("$.jobId");
        assertThat(countAssignments(gpuId)).isEqualTo(1);

        // Let the job's two-hour lease run out.
        jobDispatcher.dispatch(client.id(),
                Instant.now().plus(Duration.ofHours(3)));
        ApiResponse deleted = call(HttpMethod.DELETE, "/resources/" + gpuId,
                client.apiKey(), null);

        assertThat(deleted.status()).as(deleted.body()).isEqualTo(200);
        assertThat(deleted.longAt("$.deletedResourceId")).isEqualTo(gpuId);
        assertThat(countAssignments(gpuId)).isZero();
        // The job itself is kept, and still reports DONE.
        ApiResponse job = call(HttpMethod.GET, "/jobs/" + jobId,
                client.apiKey(), null);
        assertThat(job.status()).isEqualTo(200);
        assertThat(job.stringAt("$.status")).isEqualTo("DONE");
        assertThat(job.<List<Integer>>at("$.resourceIds")).isEmpty();
    }

    private int countAssignments(final long resourceId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM job_resource WHERE resource_id = ?",
                Integer.class, resourceId);
    }
}
