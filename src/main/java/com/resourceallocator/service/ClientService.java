package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.dto.CreateClientRequest;
import com.resourceallocator.dto.CreateClientResponse;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.model.Organization;
import com.resourceallocator.repository.OrganizationRepository;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers client organizations and issues their API keys.
 */
@Service
public class ClientService {

    /** Number of random bytes in an API key (256 bits). */
    private static final int API_KEY_BYTES = 32;

    /** Source of randomness for API keys. */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Persists organizations. */
    private final OrganizationRepository organizationRepository;

    /**
     * Creates the service.
     *
     * @param organizationRepository repository used to persist organizations
     */
    public ClientService(
            final OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
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
            throw duplicateName(orgName);
        }
        return CreateClientResponse.from(organization, apiKey);
    }

    private static ConflictException duplicateName(final String orgName) {
        return new ConflictException(
                "Organization '" + orgName + "' already exists.");
    }

    private static String generateApiKey() {
        byte[] bytes = new byte[API_KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
