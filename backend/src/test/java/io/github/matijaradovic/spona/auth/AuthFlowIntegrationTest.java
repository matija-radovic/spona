package io.github.matijaradovic.spona.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.auth.token.EmailVerificationPurpose;
import io.github.matijaradovic.spona.auth.token.EmailVerificationTokenRepository;
import io.github.matijaradovic.spona.common.util.SecureTokens;
import io.github.matijaradovic.spona.support.ContainersConfig;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Register, verify and resend over the real chain; no request here carries a token. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
@Transactional
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private EmailVerificationTokenRepository tokens;

    @Autowired
    private PasswordEncoder passwords;

    @PersistenceContext
    private EntityManager entities;

    @Test
    void registerWritesALockedAccountAndALiveSignupToken() throws Exception {
        mvc.perform(register("novi", "novi@example.com", "Lozinka123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("novi@example.com"));

        User user = users.findByUsername("novi").orElseThrow();
        assertThat(user.getEmailVerifiedAt()).isNull();

        var token = tokens.findByUserAndPurposeAndConsumedAtIsNull(user, EmailVerificationPurpose.SIGNUP)
                .orElseThrow();
        assertThat(token.getEmail()).isEqualTo("novi@example.com");
        assertThat(token.getTokenHash()).hasSize(SecureTokens.HASH_LENGTH);
        assertThat(token.getExpiresAt()).isAfter(Instant.now().plusSeconds(23 * 3600));
    }

    @Test
    void registerStoresADigestAndNotThePassword() throws Exception {
        mvc.perform(register("tajni", "tajni@example.com", "Lozinka123"))
                .andExpect(status().isCreated());

        String digest = users.findByUsername("tajni").orElseThrow().getPasswordDigest();
        assertThat(digest).isNotEqualTo("Lozinka123").startsWith("$2");
        assertThat(passwords.matches("Lozinka123", digest)).isTrue();
    }

    /** The address is the key `user_email_key` lowercases, and the token row stores it that way. */
    @Test
    void registerNormalisesTheAddress() throws Exception {
        mvc.perform(register("velika", "Velika@Example.COM", "Lozinka123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("velika@example.com"));

        assertThat(users.findByUsername("velika").orElseThrow().getEmail()).isEqualTo("velika@example.com");
    }

    @Test
    void registerRefusesAReservedHandle() throws Exception {
        mvc.perform(register("settings", "settings@example.com", "Lozinka123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/handle-reserved"));

        assertThat(users.findByUsername("settings")).isEmpty();
    }

    @Test
    void registerRefusesAHandleSomebodyHolds() throws Exception {
        users.saveAndFlush(TestFixtures.user("zauzet"));

        mvc.perform(register("zauzet", "drugi@example.com", "Lozinka123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/username-taken"));
    }

    @Test
    void registerRefusesAnAddressThatIsAlreadyRegistered() throws Exception {
        users.saveAndFlush(TestFixtures.user("prvi"));

        mvc.perform(register("drugi", "PRVI@example.com", "Lozinka123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/email-taken"));
    }

    @Test
    void verifyStampsTheAccountAndSpendsTheToken() throws Exception {
        User user = users.saveAndFlush(TestFixtures.user("potvrda"));
        tokens.saveAndFlush(TestFixtures.token(
                user, EmailVerificationPurpose.SIGNUP, user.getEmail(), "zivi-token"));
        clearContext();

        mvc.perform(verify("zivi-token"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(users.findById(user.getId()).orElseThrow().getEmailVerifiedAt()).isNotNull();
        assertThat(tokens.findByTokenHash(SecureTokens.hash("zivi-token")).orElseThrow().getConsumedAt())
                .isNotNull();
    }

    @Test
    void verifyRefusesATokenNobodyIssued() throws Exception {
        mvc.perform(verify("izmisljen"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.token").isString());
    }

    @Test
    void verifyRefusesATokenThatWasAlreadySpent() throws Exception {
        User user = users.saveAndFlush(TestFixtures.user("dvaput"));
        tokens.saveAndFlush(TestFixtures.token(
                user, EmailVerificationPurpose.SIGNUP, user.getEmail(), "jedan-put"));
        clearContext();

        mvc.perform(verify("jedan-put")).andExpect(status().isNoContent());
        mvc.perform(verify("jedan-put")).andExpect(status().isUnprocessableContent());
    }

    /** The CHECK forbids inserting an expired row, so the row is aged after it is written. */
    @Test
    void verifyRefusesAnExpiredLink() throws Exception {
        User user = users.saveAndFlush(TestFixtures.user("istekao"));
        tokens.saveAndFlush(TestFixtures.token(
                user, EmailVerificationPurpose.SIGNUP, user.getEmail(), "stari-token"));
        entities.createNativeQuery("""
                UPDATE email_verification_tokens
                   SET created_at = now() - interval '30 hours', expires_at = now() - interval '6 hours'
                 WHERE user_id = :id""")
                .setParameter("id", user.getId())
                .executeUpdate();
        clearContext();

        mvc.perform(verify("stari-token"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.token").isString());

        assertThat(users.findById(user.getId()).orElseThrow().getEmailVerifiedAt()).isNull();
    }

    @Test
    void aChangeTokenWritesTheNewAddressAndKeepsTheAccountVerified() throws Exception {
        User user = TestFixtures.user("promena");
        user.setEmailVerifiedAt(Instant.now().minusSeconds(3600));
        users.saveAndFlush(user);
        tokens.saveAndFlush(TestFixtures.token(
                user, EmailVerificationPurpose.CHANGE, "nova@example.com", "change-token"));
        clearContext();

        mvc.perform(verify("change-token")).andExpect(status().isNoContent());

        User confirmed = users.findById(user.getId()).orElseThrow();
        assertThat(confirmed.getEmail()).isEqualTo("nova@example.com");
        assertThat(confirmed.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void resendAnswersAKnownAndAnUnknownAddressIdentically() throws Exception {
        User known = users.saveAndFlush(TestFixtures.user("poznat"));
        clearContext();

        MvcResult registered = mvc.perform(resend("poznat@example.com")).andReturn();
        MvcResult stranger = mvc.perform(resend("niko@example.com")).andReturn();

        assertThat(registered.getResponse().getStatus())
                .isEqualTo(stranger.getResponse().getStatus())
                .isEqualTo(204);
        assertThat(registered.getResponse().getContentAsString())
                .isEqualTo(stranger.getResponse().getContentAsString())
                .isEmpty();

        assertThat(tokens.findByUserAndPurposeAndConsumedAtIsNull(known, EmailVerificationPurpose.SIGNUP))
                .isPresent();
    }

    /** `verification_live_key` would reject the second insert, so the first has to be consumed. */
    @Test
    void aSecondResendReplacesTheLiveTokenInsteadOfCollidingWithIt() throws Exception {
        mvc.perform(register("ponovo", "ponovo@example.com", "Lozinka123"))
                .andExpect(status().isCreated());

        User user = users.findByUsername("ponovo").orElseThrow();
        String first = liveHash(user);

        mvc.perform(resend("ponovo@example.com")).andExpect(status().isNoContent());
        String second = liveHash(user);

        mvc.perform(resend("PONOVO@example.com")).andExpect(status().isNoContent());
        String third = liveHash(user);

        assertThat(first).isNotEqualTo(second);
        assertThat(second).isNotEqualTo(third);
    }

    @Test
    void resendWritesNothingForAnAccountThatIsAlreadyVerified() throws Exception {
        User user = TestFixtures.user("potvrdjen");
        user.setEmailVerifiedAt(Instant.now());
        users.saveAndFlush(user);
        clearContext();

        mvc.perform(resend("potvrdjen@example.com")).andExpect(status().isNoContent());

        assertThat(tokens.findByUserAndPurposeAndConsumedAtIsNull(user, EmailVerificationPurpose.SIGNUP))
                .isEmpty();
    }

    /** The signup form calls this before anyone has a token, so it has to answer a guest. */
    @Test
    void theAvailabilityCheckIsPublicAndSeesTheNewAccount() throws Exception {
        mvc.perform(get("/users").param("username", "slobodan"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        mvc.perform(register("slobodan", "slobodan@example.com", "Lozinka123"))
                .andExpect(status().isCreated());

        mvc.perform(get("/users").param("username", "slobodan")).andExpect(content().string("false"));
        mvc.perform(get("/users").param("username", "admin")).andExpect(content().string("false"));
    }

    private String liveHash(User user) {
        clearContext();
        return tokens.findByUserAndPurposeAndConsumedAtIsNull(user, EmailVerificationPurpose.SIGNUP)
                .orElseThrow()
                .getTokenHash();
    }

    /** The handler writes through the same persistence context, so a read has to come off the row. */
    private void clearContext() {
        entities.flush();
        entities.clear();
    }

    private static RequestBuilder register(String username, String email, String password) {
        return post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "email": "%s", "password": "%s", "fullName": "",
                         "bio": null, "avatarUrl": null, "location": null, "otherContactInfo": []}"""
                        .formatted(username, email, password));
    }

    private static RequestBuilder verify(String token) {
        return post("/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s"}""".formatted(token));
    }

    private static RequestBuilder resend(String email) {
        return post("/auth/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}""".formatted(email));
    }
}
