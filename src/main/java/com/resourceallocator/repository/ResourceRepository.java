package com.resourceallocator.repository;

import com.resourceallocator.model.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
