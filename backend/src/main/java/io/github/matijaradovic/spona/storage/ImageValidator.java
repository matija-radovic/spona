package io.github.matijaradovic.spona.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

import io.github.matijaradovic.spona.common.error.UnprocessableException;

final class ImageValidator {

    static final String PNG = "image/png";
    static final int AVATAR_SIDE = 256;
    static final int AVATAR_MAX_BYTES = 512 * 1024;

    private static final String FIELD = "avatar";
    private static final String DATA_URL_PREFIX = "data:" + PNG + ";base64,";
    private static final int MAX_DATA_URL_LENGTH = DATA_URL_PREFIX.length() + 4 * ((AVATAR_MAX_BYTES + 2) / 3);
    private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };

    private ImageValidator() {
    }

    static byte[] avatar(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith(DATA_URL_PREFIX)) {
            throw refused("The avatar must be a PNG data URL.");
        }
        if (dataUrl.length() > MAX_DATA_URL_LENGTH) {
            throw tooLarge();
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(DATA_URL_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw refused("The avatar is not valid base64.");
        }
        if (bytes.length > AVATAR_MAX_BYTES) {
            throw tooLarge();
        }
        if (!hasPngHeader(bytes)) {
            throw refused("The avatar is not a PNG.");
        }
        ByteBuffer header = ByteBuffer.wrap(bytes);
        if (header.getInt(16) != AVATAR_SIDE || header.getInt(20) != AVATAR_SIDE) {
            throw refused("The avatar must be exactly %d by %d pixels.".formatted(AVATAR_SIDE, AVATAR_SIDE));
        }
        if (!decodes(bytes)) {
            throw refused("The avatar is not a valid PNG.");
        }
        return bytes;
    }

    private static boolean hasPngHeader(byte[] bytes) {
        return bytes.length >= 24
                && Arrays.equals(bytes, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length)
                && "IHDR".equals(new String(bytes, 12, 4, StandardCharsets.US_ASCII));
    }

    private static boolean decodes(byte[] bytes) {
        try {
            return ImageIO.read(new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) != null;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private static UnprocessableException tooLarge() {
        return refused("At most %d KB.".formatted(AVATAR_MAX_BYTES / 1024));
    }

    private static UnprocessableException refused(String message) {
        return UnprocessableException.on(FIELD, message);
    }
}
