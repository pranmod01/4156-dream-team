package com.resourceallocator;

// AI-edited (Claude Code, Claude Opus 5.5): Checkstyle fixes only

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the resource allocator service.
 */
@SpringBootApplication
public class ResourceAllocatorApplication {

    /**
     * Protected so Checkstyle treats this as a non-utility class while
     * still letting Spring create its configuration proxy.
     */
    protected ResourceAllocatorApplication() {
    }

    /**
     * Starts the Spring application.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(ResourceAllocatorApplication.class, args);
    }

}
