package com.resourceallocator.repository;

import com.resourceallocator.model.Job;
import com.resourceallocator.model.JobStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link Job} entities.
 */
public interface JobRepository extends JpaRepository<Job, Long> {

    /**
     * Lists an organization's jobs in queue order: oldest submission
     * first, with ties broken by identifier.
     *
     * @param organizationId the submitting organization's identifier
     * @return the organization's jobs in queue order
     */
    List<Job> findByOrganizationIdOrderBySubmittedAtAscIdAsc(
            Long organizationId);

    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    /**
     * Lists an organization's jobs in one state, in queue order.
     *
     * @param organizationId the submitting organization's identifier
     * @param status the state to select
     * @return the matching jobs, oldest submission first
     */
    List<Job> findByOrganizationIdAndStatusOrderBySubmittedAtAscIdAsc(
            Long organizationId, JobStatus status);

    /**
     * Finds a job only if it belongs to the given organization.
     *
     * @param id the job identifier
     * @param organizationId the submitting organization's identifier
     * @return the job, if it exists and belongs to the organization
     */
    Optional<Job> findByIdAndOrganizationId(Long id, Long organizationId);

    /**
     * Lists the organizations that have at least one job not in the
     * given state.
     *
     * @param status the state to exclude
     * @return identifiers of the organizations with other jobs
     */
    @Query("SELECT DISTINCT j.organizationId FROM Job j "
            + "WHERE j.status <> :status")
    List<Long> findOrganizationIdsWithJobsNotIn(
            @Param("status") JobStatus status);

    /**
     * Lists the identifiers of an organization's resources that are
     * currently assigned to a running job.
     *
     * @param organizationId the owning organization's identifier
     * @return identifiers of resources held by running jobs
     */
    @Query(value = """
            SELECT DISTINCT jr.resource_id
            FROM job_resource jr
            JOIN job j ON j.id = jr.job_id
            WHERE j.organization_id = :organizationId
              AND j.job_status = 'RUNNING'
            """, nativeQuery = true)
    List<Long> findResourceIdsHeldByRunningJobs(
            @Param("organizationId") Long organizationId);

    /**
     * Lists the resources assigned to a job.
     *
     * @param jobId the job identifier
     * @return identifiers of the job's resources, in ascending order
     */
    @Query(value = "SELECT resource_id FROM job_resource "
            + "WHERE job_id = :jobId ORDER BY resource_id",
            nativeQuery = true)
    List<Long> findResourceIdsByJobId(@Param("jobId") Long jobId);

    /**
     * Records that a resource is assigned to a job.
     *
     * @param jobId the job identifier
     * @param resourceId the resource identifier
     */
    @Modifying
    @Query(value = "INSERT INTO job_resource (job_id, resource_id) "
            + "VALUES (:jobId, :resourceId)", nativeQuery = true)
    void assignResource(@Param("jobId") Long jobId,
            @Param("resourceId") Long resourceId);

    /**
     * Lists which running job holds each of an organization's busy
     * resources, as {@code [resourceId, jobId]} pairs.
     *
     * @param organizationId the owning organization's identifier
     * @return one pair per resource held by a running job
     */
    @Query(value = """
            SELECT jr.resource_id, jr.job_id
            FROM job_resource jr
            JOIN job j ON j.id = jr.job_id
            WHERE j.organization_id = :organizationId
              AND j.job_status = 'RUNNING'
            """, nativeQuery = true)
    List<Object[]> findRunningAssignments(
            @Param("organizationId") Long organizationId);
    // END AI-generated
}
