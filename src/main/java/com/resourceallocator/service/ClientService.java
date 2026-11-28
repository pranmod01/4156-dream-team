package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.dto.CreateClientRequest;
import com.resourceallocator.dto.ClientResponse;
import com.resourceallocator.dto.CreateClientResponse;
import com.resourceallocator.dto.UpdateClientLimitsRequest;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.exception.NotFoundException;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.OrganizationRepository;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers client organizations, issues their API keys, and changes
 * their usage limits.
 */
@Service
public class ClientService {

    /** Number of random bytes in an API key (256 bits). */
    private static final int API_KEY_BYTES = 32;

    /** Source of randomness for API keys. */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Persists organizations. */
    private final OrganizationRepository organizationRepository;

    /** Checks the organization's queued jobs against new limits. */
    private final JobRepository jobRepository;

    /** Starts queued jobs that a raised limit lets run. */
    private final JobDispatcher jobDispatcher;

    /**
     * Creates the service.
     *
     * @param organizationRepository repository used to persist organizations
     * @param jobRepository repository used to read queued jobs
     * @param jobDispatcher dispatcher run after limits change
     */
    public ClientService(
            final OrganizationRepository organizationRepository,
            final JobRepository jobRepository,
            final JobDispatcher jobDispatcher) {
        this.organizationRepository = organizationRepository;
        this.jobRepository = jobRepository;
        this.jobDispatcher = jobDispatcher;
    }

    /**
     * Registers a new organization and issues it an API key. Only the
     * key's hash is stored.
     *
     * @param request the organization to create
     * @return the created organization and its raw API key
     * @throws ConflictException if the organization name is already taken
     */
    @Transactional
    public CreateClientResponse createClient(
            final CreateClientRequest request) {
        // The 255-character limit was already checked on the untrimmed
        // name (see CreateClientRequest).
        String orgName = request.orgName().trim();
        if (organizationRepository.existsByOrgName(orgName)) {
            throw duplicateName(orgName);
        }
        String apiKey = generateApiKey();
        Organization organization = new Organization(
                orgName,
                ApiKeyHasher.hash(apiKey),
                request.maxCpus(),
                request.maxGpus());
        try {
            // Flush now so a concurrent registration of the same name
            // fails here, as a 409, rather than at commit time.
            organization = organizationRepository.saveAndFlush(organization);
        } catch (DataIntegrityViolationException e) {
            throw duplicateName(orgName, e);
        }
        return CreateClientResponse.from(organization, apiKey);
    }

    /**
     * Changes the organization's usage limits.
     *
     * <p>The organization's row is locked first, the same lock every
     * dispatch takes, so the change never lands in the middle of a
     * dispatch: a dispatch either finishes under the old limits before
     * this runs, or waits and then sees the new ones. Job submission takes
     * the lock too, so no job larger than the new limit can be queued
     * while this runs.
     *
     * <p>Running jobs are never stopped. If a limit is lowered below the
     * units running jobs already hold, those jobs keep running until their
     * leases end, and no new job of that type starts until usage falls
     * below the new limit. A limit cannot be lowered below the units a
     * queued job requests, since that job could then never run; such
     * jobs must be deleted first. After the change the dispatcher runs,
     * so jobs that a raised limit allows start at once.
     *
     * @param organization the authenticated organization
     * @param request the new limits; a {@code null} field keeps its value
     * @return the organization with its updated limits
     * @throws InvalidRequestException if neither limit is given
     * @throws ConflictException if a queued job requests more units than
     *         the new limit allows
     */
    @Transactional
    public ClientResponse updateLimits(final Organization organization,
            final UpdateClientLimitsRequest request) {
        if (request.maxCpus() == null && request.maxGpus() == null) {
            throw new InvalidRequestException(
                    "At least one of maxCpus or maxGpus is required.");
        }
        // Read the limits from the locked row, not from the copy loaded
        // during authentication, which may be out of date.
        Organization locked = organizationRepository
                .findByIdForUpdate(organization.getId())
                .orElseThrow(() -> new NotFoundException("Organization "
                        + organization.getId() + " was not found."));
        int maxCpus = request.maxCpus() == null
                ? locked.getMaxCpus() : request.maxCpus();
        int maxGpus = request.maxGpus() == null
                ? locked.getMaxGpus() : request.maxGpus();
        checkQueuedJobsFit(locked.getId(), ResourceType.CPU, maxCpus);
        checkQueuedJobsFit(locked.getId(), ResourceType.GPU, maxGpus);
        locked.setLimits(maxCpus, maxGpus);
        organizationRepository.flush();
        // Same transaction, so the dispatcher reuses the lock and sees the
        // new limits.
        jobDispatcher.dispatch(locked.getId());
        return ClientResponse.from(locked);
    }

    private void checkQueuedJobsFit(final Long organizationId,
            final ResourceType type, final int limit) {
        if (jobRepository.existsWithMoreUnitsThan(
                organizationId, JobStatus.QUEUED, type, limit)) {
            throw new ConflictException("A queued " + type + " job requests "
                    + "more than " + limit + " units; delete it before "
                    + "lowering the " + type + " limit to " + limit + ".");
        }
    }

    private static ConflictException duplicateName(final String orgName) {
        return duplicateName(orgName, null);
    }

    // AI-edited (Claude Code, Claude Opus 5.5): keeps the database error
    // as the cause (PMD PreserveStackTrace)
    private static ConflictException duplicateName(final String orgName,
            final Throwable cause) {
        return new ConflictException(
                "Organization '" + orgName + "' already exists.", cause);
    }

    private static String generateApiKey() {
        byte[] bytes = new byte[API_KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
