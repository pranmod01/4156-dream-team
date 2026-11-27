package com.resourceallocator.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Requires an API key on every job and resource endpoint, and on
 * {@code /clients/me}. Registration ({@code POST /clients}) stays open.
 */
@Configuration
public class ApiKeyAuthConfig implements WebMvcConfigurer {

    /** Interceptor that performs the API key check. */
    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;

    /**
     * Creates the configuration.
     *
     * @param apiKeyAuthInterceptor interceptor that checks API keys
     */
    public ApiKeyAuthConfig(final ApiKeyAuthInterceptor apiKeyAuthInterceptor) {
        this.apiKeyAuthInterceptor = apiKeyAuthInterceptor;
    }

    /**
     * Registers the API key check for the protected endpoints.
     *
     * @param registry the interceptor registry
     */
    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/jobs", "/jobs/**",
                        "/resources", "/resources/**",
                        // AI-edited (Claude Code, Claude Opus 5.5)
                        "/clients/me");
    }
}
