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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.github.matijaradovic.spona.auth.token.EmailVerificationPurpose;
import io.github.matijaradovic.spona.auth.token.EmailVerificationTokenRepository;
import io.github.matijaradovic.spona.auth.token.PasswordResetTokenRepository;
import io.github.matijaradovic.spona.common.util.SecureTokens;
import io.github.matijaradovic.spona.support.ContainersConfig;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
    private PasswordResetTokenRepository resets;

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

    /** The whole point of B11: a person who registered can get back in and stay in across a refresh. */
    @Test
    void registerThenVerifyThenLogInAndTheTokenOpensAuthMe() throws Exception {
        mvc.perform(register("prijava", "prijava@example.com", "Lozinka123"))
                .andExpect(status().isCreated());

        User user = users.findByUsername("prijava").orElseThrow();
        rewriteLiveLink(user, "link-token");

        mvc.perform(verify("link-token")).andExpect(status().isNoContent());
        clearContext();

        String token = tokenFrom(mvc.perform(login("prijava", "Lozinka123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("prijava"))
                .andReturn());

        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.username").value("prijava"));
    }

    @Test
    void loginTakesTheAddressInPlaceOfTheUsername() throws Exception {
        verified("adresa", "Lozinka123");

        mvc.perform(login("ADRESA@example.com", "Lozinka123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("adresa"));
    }

    @Test
    void loginRefusesAWrongPasswordWith401() throws Exception {
        verified("pogresna", "Lozinka123");

        mvc.perform(login("pogresna", "Nije123456"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void loginRefusesAnUnverifiedAccountWithTheTypeTheFormBranchesOn() throws Exception {
        User user = TestFixtures.user("cekanje");
        user.setPasswordDigest(passwords.encode("Lozinka123"));
        users.saveAndFlush(user);
        clearContext();

        mvc.perform(login("cekanje", "Lozinka123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/email-not-verified"));

        assertThat(tokens.findByUserAndPurposeAndConsumedAtIsNull(user, EmailVerificationPurpose.SIGNUP))
                .as("the 403 arm mails a fresh link, so the client never has to ask for one")
                .isPresent();
    }

    @Test
    void aWrongPasswordOnAnUnverifiedAccountWritesNoToken() throws Exception {
        User user = TestFixtures.user("tisina");
        user.setPasswordDigest(passwords.encode("Lozinka123"));
        users.saveAndFlush(user);
        clearContext();

        mvc.perform(login("tisina", "Pogresna123")).andExpect(status().isUnauthorized());

        assertThat(tokens.findByUserAndPurposeAndConsumedAtIsNull(user, EmailVerificationPurpose.SIGNUP))
                .isEmpty();
    }

    /** Answering the unknown arm differently would turn login into an account-existence oracle. */
    @Test
    void anUnknownIdentifierAnswersExactlyAsAWrongPasswordDoes() throws Exception {
        verified("postoji", "Lozinka123");

        MvcResult known = mvc.perform(login("postoji", "Nije123456")).andReturn();
        MvcResult stranger = mvc.perform(login("nepostoji", "Nije123456")).andReturn();

        assertThat(known.getResponse().getStatus())
                .isEqualTo(stranger.getResponse().getStatus())
                .isEqualTo(401);
        assertThat(known.getResponse().getContentAsString())
                .isEqualTo(stranger.getResponse().getContentAsString());
    }

    @Test
    void authMeAndLogoutBothRefuseAGuest() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());
    }

    /** Contract §1: there is nothing to revoke, so the same token has to keep working after it. */
    @Test
    void logoutAnswers204AndLeavesTheTokenUsable() throws Exception {
        verified("odjava", "Lozinka123");
        String token = tokenFrom(mvc.perform(login("odjava", "Lozinka123")).andReturn());

        mvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetAnswersAKnownAndAnUnknownAddressIdentically() throws Exception {
        User known = verified("zaboravio", "Lozinka123");

        MvcResult registered = mvc.perform(passwordReset("zaboravio@example.com")).andReturn();
        MvcResult stranger = mvc.perform(passwordReset("niko@example.com")).andReturn();

        assertThat(registered.getResponse().getStatus())
                .isEqualTo(stranger.getResponse().getStatus())
                .isEqualTo(204);
        assertThat(registered.getResponse().getContentAsString())
                .isEqualTo(stranger.getResponse().getContentAsString())
                .isEmpty();

        clearContext();
        assertThat(resets.findByUserAndConsumedAtIsNull(known)).hasSize(1);
    }

    @Test
    void passwordResetWritesALiveRowForAnUnverifiedAccountToo() throws Exception {
        User user = users.saveAndFlush(TestFixtures.user("nepotvrdjen"));
        clearContext();

        mvc.perform(passwordReset("nepotvrdjen@example.com")).andExpect(status().isNoContent());

        clearContext();
        assertThat(resets.findByUserAndConsumedAtIsNull(user)).hasSize(1);
    }

    @Test
    void aSecondRequestLeavesTheFirstLinkAliveBesideIt() throws Exception {
        User user = verified("dvaput", "Lozinka123");

        mvc.perform(passwordReset("dvaput@example.com")).andExpect(status().isNoContent());
        mvc.perform(passwordReset("DVAPUT@Example.com")).andExpect(status().isNoContent());

        clearContext();
        assertThat(resets.findByUserAndConsumedAtIsNull(user)).hasSize(2);
    }

    @Test
    void resetMakesTheNewPasswordWorkAndTheOldOneStop() throws Exception {
        User user = verified("nova", "StaraLozinka1");

        mvc.perform(passwordReset("nova@example.com")).andExpect(status().isNoContent());
        rewriteLiveReset(user, "reset-token");

        mvc.perform(confirmReset("reset-token", "NovaLozinka1")).andExpect(status().isNoContent());
        clearContext();

        mvc.perform(login("nova", "NovaLozinka1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("nova"));
        mvc.perform(login("nova", "StaraLozinka1")).andExpect(status().isUnauthorized());
    }

    @Test
    void aResetSpendsEveryLiveLinkOfThatUser() throws Exception {
        User user = verified("sve", "Lozinka123");
        resets.saveAndFlush(TestFixtures.resetToken(user, "prvi"));
        resets.saveAndFlush(TestFixtures.resetToken(user, "drugi"));
        resets.saveAndFlush(TestFixtures.resetToken(user, "treci"));
        clearContext();

        mvc.perform(confirmReset("drugi", "NovaLozinka1")).andExpect(status().isNoContent());
        clearContext();

        assertThat(resets.findByUserAndConsumedAtIsNull(user)).isEmpty();
        assertThat(resets.findByTokenHash(SecureTokens.hash("prvi")).orElseThrow().getConsumedAt())
                .isNotNull();
        mvc.perform(confirmReset("prvi", "JosJedna1")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void aResetLeavesATokenMintedBeforeItUsable() throws Exception {
        User user = verified("sesija", "StaraLozinka1");
        String minted = tokenFrom(mvc.perform(login("sesija", "StaraLozinka1")).andReturn());
        resets.saveAndFlush(TestFixtures.resetToken(user, "sesija-token"));
        clearContext();

        mvc.perform(confirmReset("sesija-token", "NovaLozinka1")).andExpect(status().isNoContent());
        clearContext();

        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + minted))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("sesija"));
    }

    @Test
    void aResetDoesNotVerifyTheAddress() throws Exception {
        User user = users.saveAndFlush(TestFixtures.user("nestampano"));
        resets.saveAndFlush(TestFixtures.resetToken(user, "nestampano-token"));
        clearContext();

        mvc.perform(confirmReset("nestampano-token", "NovaLozinka1")).andExpect(status().isNoContent());
        clearContext();

        assertThat(users.findById(user.getId()).orElseThrow().getEmailVerifiedAt()).isNull();
        mvc.perform(login("nestampano", "NovaLozinka1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/email-not-verified"));
    }

    @Test
    void confirmRefusesAResetTokenNobodyIssued() throws Exception {
        mvc.perform(confirmReset("izmisljen", "NovaLozinka1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.token").isString());
    }

    @Test
    void authMeServesTheFieldListTheClientReads() throws Exception {
        User user = verified("profil", "Lozinka123");
        user.setFullName("Marko Markovic");
        user.setBio("Student");
        user.setLocation("Novi Sad");
        user.setOtherContactInfo(TestFixtures.contacts());
        users.saveAndFlush(user);
        String token = tokenFrom(mvc.perform(login("profil", "Lozinka123")).andReturn());
        clearContext();

        mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorType").value("user"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.fullName").value("Marko Markovic"))
                .andExpect(jsonPath("$.email").value("profil@example.com"))
                .andExpect(jsonPath("$.bio").value("Student"))
                .andExpect(jsonPath("$.location").value("Novi Sad"))
                .andExpect(jsonPath("$.otherContactInfo[0].type").value("email"))
                .andExpect(jsonPath("$.passwordDigest").doesNotExist());
    }

    private User verified(String username, String password) {
        User user = TestFixtures.user(username);
        user.setPasswordDigest(passwords.encode(password));
        user.setEmailVerifiedAt(Instant.now());
        users.saveAndFlush(user);
        clearContext();
        return user;
    }

    /** Only the digest is stored, so the link the test wants to follow has to be written in. */
    private void rewriteLiveLink(User user, String plaintext) {
        entities.createNativeQuery("""
                UPDATE email_verification_tokens SET token_hash = :hash
                 WHERE user_id = :id AND consumed_at IS NULL""")
                .setParameter("hash", SecureTokens.hash(plaintext))
                .setParameter("id", user.getId())
                .executeUpdate();
        clearContext();
    }

    /** The reset table's live index is plain, so this rewrites every live row the user holds. */
    private void rewriteLiveReset(User user, String plaintext) {
        entities.createNativeQuery("""
                UPDATE password_reset_tokens SET token_hash = :hash
                 WHERE user_id = :id AND consumed_at IS NULL""")
                .setParameter("hash", SecureTokens.hash(plaintext))
                .setParameter("id", user.getId())
                .executeUpdate();
        clearContext();
    }

    private static String tokenFrom(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
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

    private static RequestBuilder login(String identifier, String password) {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"identifier": "%s", "password": "%s"}""".formatted(identifier, password));
    }

    private static RequestBuilder resend(String email) {
        return post("/auth/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}""".formatted(email));
    }

    private static RequestBuilder passwordReset(String email) {
        return post("/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}""".formatted(email));
    }

    private static RequestBuilder confirmReset(String token, String newPassword) {
        return post("/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "newPassword": "%s"}""".formatted(token, newPassword));
    }
}
