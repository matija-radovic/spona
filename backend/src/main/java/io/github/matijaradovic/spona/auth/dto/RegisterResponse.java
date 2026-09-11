package io.github.matijaradovic.spona.auth.dto;

public record RegisterResponse(String email, String message) {

    public static RegisterResponse checkYourInbox(String email) {
        return new RegisterResponse(email, "Check your inbox for the confirmation link.");
    }
}
