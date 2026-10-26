package com.resourceallocator.controller;
// AI-generated (Claude Code, Claude Opus 5.5)

import com.resourceallocator.exception.InvalidRequestException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Parsing helpers for query parameters shared by the controllers.
 */
final class RequestParams {

    private RequestParams() {
    }

    /**
     * Parses a case-insensitive enum query parameter.
     *
     * @param <E> the enum type
     * @param type the enum class
     * @param parameter the parameter name, used in the error message
     * @param value the raw value, or {@code null} if absent
     * @return the enum constant, or {@code null} if {@code value} is
     *         {@code null}
     * @throws InvalidRequestException if the value names no constant
     */
    static <E extends Enum<E>> E parseEnum(final Class<E> type,
            final String parameter, final String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid " + parameter + " '"
                    + value + "'; expected one of "
                    + Arrays.toString(type.getEnumConstants()) + ".");
        }
    }
}
