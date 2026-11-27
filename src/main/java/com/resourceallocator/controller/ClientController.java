package com.resourceallocator.controller;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.auth.ApiKeyAuthInterceptor;
import com.resourceallocator.dto.ClientResponse;
import com.resourceallocator.dto.CreateClientRequest;
import com.resourceallocator.dto.CreateClientResponse;
import com.resourceallocator.dto.UpdateClientLimitsRequest;
import com.resourceallocator.model.Organization;
import com.resourceallocator.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for registering client organizations and changing their usage
 * limits. Registration needs no API key, since this is where keys are
 * issued; changing limits needs the organization's key.
 */
@RestController
@RequestMapping("/clients")
public class ClientController {

    /** Performs client registration. */
    private final ClientService clientService;

    /**
     * Creates the controller.
     *
     * @param clientService service that registers clients
     */
    public ClientController(final ClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * Registers a new organization and returns its API key.
     *
     * @param request the organization to create
     * @return 201 Created with the organization and its API key
     */
    @PostMapping
    public ResponseEntity<CreateClientResponse> createClient(
            @Valid @RequestBody final CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clientService.createClient(request));
    }

    /**
     * Changes the caller's usage limits. Running jobs are not stopped, so
     * a lowered limit holds back only jobs that have not started yet.
     *
     * @param organization the organization identified by the API key
     * @param request the new limits; an omitted field keeps its value
     * @return 200 OK with the organization and its updated limits
     */
    @PatchMapping("/me")
    public ClientResponse updateLimits(
            @RequestAttribute(ApiKeyAuthInterceptor.ORGANIZATION_ATTRIBUTE)
            final Organization organization,
            @Valid @RequestBody final UpdateClientLimitsRequest request) {
        return clientService.updateLimits(organization, request);
    }
}
