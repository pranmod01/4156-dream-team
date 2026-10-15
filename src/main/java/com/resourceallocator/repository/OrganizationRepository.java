package com.resourceallocator.repository;

import com.resourceallocator.model.Organization;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
