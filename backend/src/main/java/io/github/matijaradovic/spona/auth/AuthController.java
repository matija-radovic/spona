package io.github.matijaradovic.spona.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.auth.dto.AuthResponse;
import io.github.matijaradovic.spona.auth.dto.LoginRequest;
import io.github.matijaradovic.spona.auth.dto.PasswordResetConfirmRequest;
import io.github.matijaradovic.spona.auth.dto.PasswordResetRequest;
import io.github.matijaradovic.spona.auth.dto.RegisterRequest;
import io.github.matijaradovic.spona.auth.dto.RegisterResponse;
import io.github.matijaradovic.spona.auth.dto.ResendRequest;
import io.github.matijaradovic.spona.auth.dto.VerifyRequest;
import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.dto.UserDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;
    private final PasswordResetService reset;

    public AuthController(AuthService auth, PasswordResetService reset) {
        this.auth = auth;
        this.reset = reset;
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

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        reset.request(request.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        reset.confirm(request.token(), request.newPassword());
    }

    /** The filter loaded and checked already, so the principal is never null here. */
    @GetMapping("/me")
    UserDto me(@CurrentUser User user) {
        return UserDto.from(user);
    }

    /** Since we use JWT, this only records the intent */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout() {
    }
}
