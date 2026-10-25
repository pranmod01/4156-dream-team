package com.resourceallocator.controller;

import com.resourceallocator.auth.ApiKeyAuthInterceptor;
import com.resourceallocator.dto.AddResourceRequest;
import com.resourceallocator.dto.DeleteResourceResponse;
import com.resourceallocator.dto.ResourceResponse;
import com.resourceallocator.dto.UpdateResourceRequest;
import com.resourceallocator.model.Organization;
import com.resourceallocator.service.ResourceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Endpoints for registering, listing, deleting, and changing the online
 * state of resources.
 * Every request needs an {@code X-API-Key} header.
 */
@RestController
@RequestMapping("/resources")
public class ResourceController {

    /** Performs the resource operations. */
    private final ResourceService resourceService;

    /**
     * Creates the controller.
     *
     * @param resourceService service that performs resource operations
     */
    public ResourceController(final ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * Registers a new resource for the caller's organization.
     *
     * @param organization the organization identified by the API key
     * @param request the resource to create
     * @return 201 Created with the new resource and its location
     */
    @PostMapping
    public ResponseEntity<ResourceResponse> addResource(
            @RequestAttribute(ApiKeyAuthInterceptor.ORGANIZATION_ATTRIBUTE)
            final Organization organization,
            @Valid @RequestBody final AddResourceRequest request) {
        ResourceResponse created =
                resourceService.addResource(organization, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Takes one of the caller's resources online or offline.
     *
     * @param organization the organization identified by the API key
     * @param resourceId identifier of the resource to update
     * @param request the new online state
     * @return 200 OK with the updated resource
     */
    @PatchMapping("/{resourceId}")
    public ResourceResponse updateResource(
            @RequestAttribute(ApiKeyAuthInterceptor.ORGANIZATION_ATTRIBUTE)
            final Organization organization,
            @PathVariable("resourceId") final Long resourceId,
            @Valid @RequestBody final UpdateResourceRequest request) {
        return resourceService.updateResource(
                organization, resourceId, request);
    }

    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    /**
     * Lists all of the caller's resources and whether each is online.
     *
     * @param organization the organization identified by the API key
     * @return 200 OK with the resources, ordered by identifier
     */
    @GetMapping
    public List<ResourceResponse> listResources(
            @RequestAttribute(ApiKeyAuthInterceptor.ORGANIZATION_ATTRIBUTE)
            final Organization organization) {
        return resourceService.listResources(organization);
    }
    // END AI-generated

    // BEGIN AI-generated (Claude, Claude Opus 5.5), reviewed by Rob
    /**
     * Permanently deletes one of the caller's resources. A resource held
     * by a running job cannot be deleted.
     *
     * @param organization the organization identified by the API key
     * @param resourceId identifier of the resource to delete
     * @return 200 OK with the deleted resource's identifier
     */
    @DeleteMapping("/{resourceId}")
    public DeleteResourceResponse deleteResource(
            @RequestAttribute(ApiKeyAuthInterceptor.ORGANIZATION_ATTRIBUTE)
            final Organization organization,
            @PathVariable("resourceId") final Long resourceId) {
        return resourceService.deleteResource(organization, resourceId);
    }
    // END AI-generated
}
