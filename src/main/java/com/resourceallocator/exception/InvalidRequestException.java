package com.resourceallocator.exception;

/**
 * Thrown when a request is well formed but violates a business rule.
 */
public class InvalidRequestException extends RuntimeException {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message explanation returned to the client
     */
    public InvalidRequestException(final String message) {
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
    public InvalidRequestException(final String message,
            final Throwable cause) {
        super(message, cause);
    }
}
