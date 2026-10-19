package com.resourceallocator.exception;
// AI-generated (Claude Code, Claude Opus 5.5)

/**
 * Thrown when the requested entity does not exist or belongs to another
 * organization.
 */
public class NotFoundException extends RuntimeException {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message explanation returned to the client
     */
    public NotFoundException(final String message) {
        super(message);
    }
}
