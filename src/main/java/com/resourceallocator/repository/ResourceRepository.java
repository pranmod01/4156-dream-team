package com.resourceallocator.repository;

import com.resourceallocator.model.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link Resource} entities.
 */
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    /**
     * Finds a resource only if it belongs to the given organization.
     *
     * @param id the resource identifier
     * @param organizationId the owning organization's identifier
     * @return the resource, if it exists and belongs to the organization
     */
    Optional<Resource> findByIdAndOrganizationId(Long id,
            Long organizationId);

    /**
     * Lists the online resources belonging to an organization.
     *
     * @param organizationId the owning organization's identifier
     * @return the organization's online resources
     */
    List<Resource> findByOrganizationIdAndOnlineTrue(Long organizationId);

    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    /**
     * Lists all resources belonging to an organization, online or offline.
     *
     * @param organizationId the owning organization's identifier
     * @return the organization's resources, ordered by identifier
     */
    List<Resource> findByOrganizationIdOrderByIdAsc(Long organizationId);
    // END AI-generated

    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    /**
     * Lists the identifiers of an organization's resources that are
     * currently assigned to a running job through {@code job_resource}.
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
    // END AI-generated
}
