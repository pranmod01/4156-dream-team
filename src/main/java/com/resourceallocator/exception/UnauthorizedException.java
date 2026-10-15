package com.resourceallocator.exception;

/**
 * Thrown when a request has a missing or invalid API key.
 */
public class UnauthorizedException extends RuntimeException {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message explanation returned to the client
     */
    public UnauthorizedException(final String message) {
        super(message);
    }
}
