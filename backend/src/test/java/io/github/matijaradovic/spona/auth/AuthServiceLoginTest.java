package io.github.matijaradovic.spona.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.matijaradovic.spona.auth.dto.AuthResponse;
import io.github.matijaradovic.spona.auth.dto.LoginRequest;
import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.UnauthorizedException;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class AuthServiceLoginTest {

    private static final String PASSWORD = "Lozinka123";

    private final UserRepository users = mock(UserRepository.class);
    private final VerificationService verification = mock(VerificationService.class);
    private final JwtService jwt = mock(JwtService.class);
    private final PasswordEncoder passwords = spy(new BCryptPasswordEncoder());

    private AuthService auth;

    @BeforeEach
    void setUp() {
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(jwt.sign(any(User.class))).thenReturn("signed.jwt.value");
        auth = new AuthService(users, verification, passwords, jwt);
    }

    @Test
    void logsInByUsername() {
        User user = verified("marko");
        when(users.findByUsername("marko")).thenReturn(Optional.of(user));

        AuthResponse response = auth.login(new LoginRequest("marko", PASSWORD));

        assertThat(response.token()).isEqualTo("signed.jwt.value");
        assertThat(response.user().username()).isEqualTo("marko");
        assertThat(response.user().email()).isEqualTo("marko@example.com");
        assertThat(response.user().id()).isEqualTo(user.getId());
    }

    @Test
    void logsInByEmailAddressWhateverCaseItWasTypedIn() {
        User user = verified("marko");
        when(users.findByEmailIgnoreCase("marko@example.com")).thenReturn(Optional.of(user));

        assertThat(auth.login(new LoginRequest("  Marko@Example.COM  ", PASSWORD)).token())
                .isEqualTo("signed.jwt.value");
    }

    @Test
    void refusesAWrongPasswordWith401() {
        User user = verified("marko");
        when(users.findByUsername("marko")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> auth.login(new LoginRequest("marko", "Pogresna123")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refusesAnIdentifierNobodyHoldsWithTheSame401() {
        assertThatThrownBy(() -> auth.login(new LoginRequest("niko", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class);
    }

    /** Skipping the compare on a miss would make "no such account" the measurably faster answer. */
    @Test
    void hashesSomethingEvenWhenNoRowWasFound() {
        assertThatThrownBy(() -> auth.login(new LoginRequest("niko", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class);

        verify(passwords).matches(eq(PASSWORD), anyString());
    }

    @Test
    void refusesAnUnverifiedAccountWithTheTypeTheFormBranchesOn() {
        User user = TestFixtures.userWithId("nepotvrdjen", UUID.randomUUID());
        user.setPasswordDigest(passwords.encode(PASSWORD));
        when(users.findByUsername("nepotvrdjen")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> auth.login(new LoginRequest("nepotvrdjen", PASSWORD)))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(thrown -> assertThat(((ForbiddenException) thrown).getType())
                        .isEqualTo(ProblemTypes.EMAIL_NOT_VERIFIED));
    }

    @Test
    void theUnverifiedArmIsOnlyReachedAfterThePasswordIsProven() {
        User user = TestFixtures.userWithId("nepotvrdjen", UUID.randomUUID());
        user.setPasswordDigest(passwords.encode(PASSWORD));
        when(users.findByUsername("nepotvrdjen")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> auth.login(new LoginRequest("nepotvrdjen", "Pogresna123")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void looksTheAddressUpOnlyWhenTheUsernameArmFoundNothing() {
        User user = verified("marko");
        when(users.findByUsername("marko")).thenReturn(Optional.of(user));

        auth.login(new LoginRequest("marko", PASSWORD));

        verify(users).findByUsername("marko");
        verify(users, never()).findByEmailIgnoreCase(anyString());
    }

    private User verified(String username) {
        User user = TestFixtures.userWithId(username, UUID.randomUUID());
        user.setPasswordDigest(passwords.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        return user;
    }
}
