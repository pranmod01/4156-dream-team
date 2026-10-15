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
}
