package com.resourceallocator.repository;

import com.resourceallocator.model.Organization;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link Organization} entities.
 */
public interface OrganizationRepository
        extends JpaRepository<Organization, Long> {

    /**
     * Finds the organization that owns the API key with the given hash.
     *
     * @param apiKeyHash SHA-256 hex digest of an API key
     * @return the owning organization, if any
     */
    Optional<Organization> findByApiKeyHash(String apiKeyHash);

    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    /**
     * Checks whether an organization with the given name already exists.
     *
     * @param orgName the organization name
     * @return {@code true} if the name is taken
     */
    boolean existsByOrgName(String orgName);

    /**
     * Finds an organization and locks its row until the transaction ends,
     * so only one dispatcher run per organization assigns resources at a
     * time.
     *
     * @param id the organization identifier
     * @return the locked organization, if it exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Organization o WHERE o.id = :id")
    Optional<Organization> findByIdForUpdate(@Param("id") Long id);
    // END AI-generated
}
