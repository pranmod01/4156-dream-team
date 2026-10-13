package com.resourceallocator.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Requires an API key on every job and resource endpoint.
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
     * Registers the API key check for job and resource endpoints.
     *
     * @param registry the interceptor registry
     */
    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/jobs", "/jobs/**",
                        "/resources", "/resources/**");
    }
}
