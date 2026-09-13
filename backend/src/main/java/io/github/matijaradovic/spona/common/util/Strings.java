package io.github.matijaradovic.spona.common.util;

import java.util.Locale;

public final class Strings {

    private Strings() {
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
