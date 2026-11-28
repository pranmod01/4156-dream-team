package com.resourceallocator.exception;
// AI-generated (Claude Code, Claude Opus 5.5)

/**
 * Thrown when a request would duplicate data that must be unique.
 */
public class ConflictException extends RuntimeException {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message explanation returned to the client
     */
    public ConflictException(final String message) {
        super(message);
    }

    // AI-generated (Claude Code, Claude Opus 5.5): keeps the cause (PMD
    // PreserveStackTrace)
    /**
     * Creates the exception, keeping the exception that caused it.
     *
     * @param message explanation returned to the client
     * @param cause the underlying exception, kept for logs and debugging
     */
    public ConflictException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
