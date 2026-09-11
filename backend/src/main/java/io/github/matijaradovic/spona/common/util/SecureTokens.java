package io.github.matijaradovic.spona.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Emailed secrets, the link carries the plaintext, the column stores only its digest */
public final class SecureTokens {

    /** 32 CSPRNG bytes, which Base64url encodes to 43 characters */
    public static final int TOKEN_BYTES = 32;

    /** The width of a SHA-256 digest in hex, and of both `token_hash` columns. */
    public static final int HASH_LENGTH = 64;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private SecureTokens() {
    }

    /** Base64url so the value survives a URL path segment unescaped */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** A lookup goes by this, so a leaked table row cannot be replayed as a link. */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not supported", e);
        }
    }
}
