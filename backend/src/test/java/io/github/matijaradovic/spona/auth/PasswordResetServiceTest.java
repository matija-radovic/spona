package io.github.matijaradovic.spona.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.matijaradovic.spona.auth.token.PasswordResetToken;
import io.github.matijaradovic.spona.auth.token.PasswordResetTokenRepository;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.SecureTokens;
import io.github.matijaradovic.spona.config.props.AppProperties;
import io.github.matijaradovic.spona.mail.MailService;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

class PasswordResetServiceTest {

    private static final String NEW_PASSWORD = "NovaLozinka1";
    private static final String TEMPLATE = "mail/password-reset";

    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MailService mail = mock(MailService.class);
    private final PasswordEncoder passwords = new BCryptPasswordEncoder();
    private final AppProperties app =
            new AppProperties("http://localhost:5173", new AppProperties.Cors(List.of()));

    private PasswordResetService reset;

    @BeforeEach
    void setUp() {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
        when(tokens.findByUserAndConsumedAtIsNull(any(User.class))).thenReturn(List.of());
        reset = new PasswordResetService(tokens, users, passwords, mail, app);
    }

    @Test
    void requestWritesALiveRowAndMailsTheLinkTheClientRouteExpects() {
        registered("marko");

        reset.request("marko@example.com");

        verify(tokens).save(any(PasswordResetToken.class));
        verify(mail).send(eq("marko@example.com"), anyString(), eq(TEMPLATE), anyMap());
        assertThat(mailedLink()).startsWith("http://localhost:5173/reset/");
    }

    /** The link carries the plaintext and the row only its digest, so the two must not match. */
    @Test
    void theRowHoldsTheDigestOfTheTokenThatWasMailed() {
        registered("marko");

        reset.request("marko@example.com");

        String link = mailedLink();
        String plaintext = link.substring(link.lastIndexOf('/') + 1);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());

        assertThat(plaintext).hasSize(43);
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(SecureTokens.hash(plaintext))
                .isNotEqualTo(plaintext);
        assertThat(saved.getValue().getExpiresAt())
                .isAfter(Instant.now().plus(PasswordResetToken.LIFETIME).minusSeconds(60));
    }

    @Test
    void requestWritesNothingForAnAddressNobodyHolds() {
        reset.request("niko@example.com");

        verifyNoInteractions(tokens, mail);
    }

    @Test
    void requestMailsAnUnverifiedAccountToo() {
        registered("nepotvrdjen");

        reset.request("nepotvrdjen@example.com");

        verify(mail).send(eq("nepotvrdjen@example.com"), anyString(), eq(TEMPLATE), anyMap());
    }

    @Test
    void requestLooksTheAddressUpLowercasedAndTrimmed() {
        registered("marko");

        reset.request("  Marko@Example.COM  ");

        verify(users).findByEmailIgnoreCase("marko@example.com");
    }

    @Test
    void requestLeavesAnEarlierLiveRowAloneInsteadOfConsumingIt() {
        User user = registered("ponovo");
        PasswordResetToken earlier = TestFixtures.resetToken(user, "prvi");
        when(tokens.findByUserAndConsumedAtIsNull(user)).thenReturn(List.of(earlier));

        reset.request("ponovo@example.com");

        assertThat(earlier.getConsumedAt()).isNull();
        verify(tokens, never()).flush();
    }

    @Test
    void confirmWritesANewDigestAndNotThePassword() {
        User user = TestFixtures.userWithId("marko", UUID.randomUUID());
        user.setPasswordDigest(passwords.encode("StaraLozinka1"));
        live(user, "zivi");

        reset.confirm("zivi", NEW_PASSWORD);

        assertThat(user.getPasswordDigest()).isNotEqualTo(NEW_PASSWORD).startsWith("$2");
        assertThat(passwords.matches(NEW_PASSWORD, user.getPasswordDigest())).isTrue();
        assertThat(passwords.matches("StaraLozinka1", user.getPasswordDigest())).isFalse();
    }

    @Test
    void confirmSpendsThePresentedRow() {
        PasswordResetToken token = live(registered("marko"), "zivi");

        reset.confirm("zivi", NEW_PASSWORD);

        assertThat(token.getConsumedAt()).isNotNull();
    }

    @Test
    void confirmSpendsEveryOtherLiveRowOfThatUser() {
        User user = registered("marko");
        PasswordResetToken presented = live(user, "zivi");
        PasswordResetToken other = TestFixtures.resetToken(user, "drugi");
        PasswordResetToken third = TestFixtures.resetToken(user, "treci");
        when(tokens.findByUserAndConsumedAtIsNull(user)).thenReturn(List.of(presented, other, third));

        reset.confirm("zivi", NEW_PASSWORD);

        assertThat(other.getConsumedAt()).isNotNull();
        assertThat(third.getConsumedAt()).isNotNull();
    }

    @Test
    void confirmRefusesATokenNobodyIssued() {
        assertThatThrownBy(() -> reset.confirm("izmisljen", NEW_PASSWORD))
                .isInstanceOf(UnprocessableException.class)
                .satisfies(thrown -> assertThat(((UnprocessableException) thrown).getErrors())
                        .containsKey("token"));
    }

    @Test
    void confirmRefusesATokenThatWasAlreadySpent() {
        PasswordResetToken spent = live(registered("marko"), "potroseni");
        spent.consume(Instant.now());

        assertThatThrownBy(() -> reset.confirm("potroseni", NEW_PASSWORD))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void confirmRefusesAnExpiredToken() {
        User user = registered("marko");
        PasswordResetToken stale = new PasswordResetToken(user, SecureTokens.hash("stari"),
                Instant.now().minusSeconds(60));
        when(tokens.findByTokenHash(SecureTokens.hash("stari"))).thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> reset.confirm("stari", NEW_PASSWORD))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void aRejectedTokenChangesNoDigest() {
        User user = registered("marko");
        String before = user.getPasswordDigest();

        assertThatThrownBy(() -> reset.confirm("izmisljen", NEW_PASSWORD))
                .isInstanceOf(UnprocessableException.class);

        assertThat(user.getPasswordDigest()).isEqualTo(before);
    }

    @Test
    void confirmDoesNotVerifyTheAddress() {
        User unverified = registered("nepotvrdjen");
        live(unverified, "zivi");

        reset.confirm("zivi", NEW_PASSWORD);

        assertThat(unverified.getEmailVerifiedAt()).isNull();
    }

    @Test
    void confirmMailsNothing() {
        live(registered("marko"), "zivi");

        reset.confirm("zivi", NEW_PASSWORD);

        verifyNoInteractions(mail);
    }

    private User registered(String username) {
        User user = TestFixtures.userWithId(username, UUID.randomUUID());
        user.setPasswordDigest(passwords.encode("StaraLozinka1"));
        when(users.findByEmailIgnoreCase(username + "@example.com")).thenReturn(Optional.of(user));
        return user;
    }

    private PasswordResetToken live(User user, String plaintext) {
        PasswordResetToken token = TestFixtures.resetToken(user, plaintext);
        when(tokens.findByTokenHash(SecureTokens.hash(plaintext))).thenReturn(Optional.of(token));
        return token;
    }

    private String mailedLink() {
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.captor();
        verify(mail).send(anyString(), anyString(), eq(TEMPLATE), variables.capture());
        return (String) variables.getValue().get("link");
    }
}
