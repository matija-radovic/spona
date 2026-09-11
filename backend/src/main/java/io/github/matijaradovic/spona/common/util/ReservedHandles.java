package io.github.matijaradovic.spona.common.util;

import java.util.Locale;
import java.util.Set;

/** Handles a static route already owns, so an account holding one could never be reached. */
public final class ReservedHandles {

    private static final Set<String> RESERVED = Set.of(
            // top-level route segments
            "jobs", "users", "companies", "posts", "feed", "invitations",
            "login", "register", "verify", "reset", "test",

            // nested static segments
            "new", "settings", "manage", "followers", "following", "comments",
            "profile", "privacy", "organizations", "account", "members",
            "management", "posting", "applications",

            // never routes
            "api", "admin", "static", "assets", "uploads", "health",
            "actuator", "swagger-ui", "well-known");

    private ReservedHandles() {
    }

    /** Matched against the lowercased handle, for both actor subtypes. */
    public static boolean isReserved(String handle) {
        return handle != null && RESERVED.contains(handle.trim().toLowerCase(Locale.ROOT));
    }
}
