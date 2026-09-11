package io.github.matijaradovic.spona.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.auth.dto.RegisterRequest;
import io.github.matijaradovic.spona.auth.dto.RegisterResponse;
import io.github.matijaradovic.spona.auth.dto.ResendRequest;
import io.github.matijaradovic.spona.auth.dto.VerifyRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verify(@Valid @RequestBody VerifyRequest request) {
        auth.verify(request.token());
    }

    /** The answer is the same either way, so this must stay a call with no condition around it. */
    @PostMapping("/verify/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resend(@Valid @RequestBody ResendRequest request) {
        auth.resendVerification(request.email());
    }
}
