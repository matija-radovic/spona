package io.github.matijaradovic.spona.common.web;

import java.net.URI;

/** The problem-type URIs the client branches on. Anything else stays `about:blank`. */
public final class ProblemTypes {

    public static final URI EMAIL_NOT_VERIFIED = URI.create("/problems/email-not-verified");
    public static final URI USERNAME_TAKEN = URI.create("/problems/username-taken");
    public static final URI EMAIL_TAKEN = URI.create("/problems/email-taken");
    public static final URI SLUG_TAKEN = URI.create("/problems/slug-taken");
    public static final URI HANDLE_RESERVED = URI.create("/problems/handle-reserved");
    public static final URI LAST_OWNER = URI.create("/problems/last-owner");
    public static final URI INVITATION_NOT_YOURS = URI.create("/problems/invitation-not-yours");
    public static final URI ALREADY_MEMBER = URI.create("/problems/already-member");

    private ProblemTypes() {
    }
}
