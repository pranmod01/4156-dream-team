package com.resourceallocator.controller;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.dto.CreateClientRequest;
import com.resourceallocator.dto.CreateClientResponse;
import com.resourceallocator.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint for registering new client organizations. Unlike the other
 * endpoints it needs no API key, since this is where keys are issued.
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
}
