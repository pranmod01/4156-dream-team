package com.resourceallocator.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes API keys so that only their digests are ever stored.
 */
public final class ApiKeyHasher {

    /** Digest algorithm; its hex output fits {@code api_key_hash}. */
    private static final String ALGORITHM = "SHA-256";

    private ApiKeyHasher() {
    }

    /**
     * Returns the lowercase hex SHA-256 digest of an API key.
     *
     * @param apiKey the raw API key
     * @return the 64-character hex digest
     */
    public static String hash(final String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashed =
                    digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    ALGORITHM + " is required by every Java platform", e);
        }
    }
}
