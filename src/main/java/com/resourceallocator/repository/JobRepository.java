package com.resourceallocator.repository;

import com.resourceallocator.model.Job;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
