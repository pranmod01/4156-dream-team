package com.resourceallocator.auth;

import com.resourceallocator.exception.UnauthorizedException;
import com.resourceallocator.model.Organization;
import com.resourceallocator.repository.OrganizationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authenticates the caller's API key before any protected endpoint runs.
 *
 * <p>Authenticating here, rather than inside each controller, means an
 * invalid key is rejected with 401 before the request body or query
 * parameters are validated. On success the caller's organization is stored
 * in the request attribute {@link #ORGANIZATION_ATTRIBUTE}.
 */
@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    /** Request header that carries the caller's API key. */
    public static final String API_KEY_HEADER = "X-API-Key";

    /** Request attribute holding the authenticated {@link Organization}. */
    public static final String ORGANIZATION_ATTRIBUTE =
            "authenticatedOrganization";

    /** Looks up organizations by API key hash. */
    private final OrganizationRepository organizationRepository;

    /**
     * Creates the interceptor.
     *
     * @param organizationRepository repository used to resolve API keys
     */
    public ApiKeyAuthInterceptor(
            final OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /**
     * Resolves the API key to an organization or rejects the request.
     *
     * @param request the current request
     * @param response the current response
     * @param handler the handler that will serve the request
     * @return {@code true} so the request continues to the handler
     * @throws UnauthorizedException if the key is missing or unknown
     */
    @Override
    public boolean preHandle(final HttpServletRequest request,
            final HttpServletResponse response, final Object handler) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            throw new UnauthorizedException(
                    "Missing " + API_KEY_HEADER + " header.");
        }
        Organization organization = organizationRepository
                .findByApiKeyHash(ApiKeyHasher.hash(apiKey))
                .orElseThrow(() ->
                        new UnauthorizedException("Invalid API key."));
        request.setAttribute(ORGANIZATION_ATTRIBUTE, organization);
        return true;
    }
}
