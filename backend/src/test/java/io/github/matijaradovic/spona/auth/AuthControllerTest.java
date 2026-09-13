package io.github.matijaradovic.spona.auth;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.auth.dto.AuthResponse;
import io.github.matijaradovic.spona.auth.dto.LoginRequest;
import io.github.matijaradovic.spona.auth.dto.RegisterRequest;
import io.github.matijaradovic.spona.auth.dto.RegisterResponse;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.UnauthorizedException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.dto.UserDto;

class AuthControllerTest {

    private static final String REGISTER = """
            {
              "username": %s,
              "email": %s,
              "password": %s,
              "fullName": %s,
              "bio": %s,
              "avatar": null,
              "location": null,
              "otherContactInfo": %s
            }""";

    private static final UUID PRINCIPAL_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final AuthService auth = mock(AuthService.class);
    private final PasswordResetService reset = mock(PasswordResetService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AuthController(auth, reset))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registersAndAnswers201WithTheAddressToCheck() throws Exception {
        when(auth.register(any(RegisterRequest.class)))
                .thenReturn(RegisterResponse.checkYourInbox("marko@example.com"));

        mvc.perform(register(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("marko@example.com"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void refusesAUsernameShorterThanThree() throws Exception {
        mvc.perform(register(body(q("ma"), q("marko@example.com"), q("Lozinka123"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Unprocessable Content"))
                .andExpect(jsonPath("$.errors.username").isString());
    }

    @Test
    void refusesAnUppercaseUsername() throws Exception {
        mvc.perform(register(body(q("Marko"), q("marko@example.com"), q("Lozinka123"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.username").isString());
    }

    @Test
    void refusesAPasswordShorterThanEight() throws Exception {
        mvc.perform(register(body(q("marko"), q("marko@example.com"), q("Loz1"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.password").isString());
    }

    @Test
    void refusesAPasswordCarryingACharacterOutsideTheSet() throws Exception {
        mvc.perform(register(body(q("marko"), q("marko@example.com"), q("Lozinka 123"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.password").isString());
    }

    @Test
    void refusesAMalformedEmail() throws Exception {
        mvc.perform(register(body(q("marko"), q("not-an-address"), q("Lozinka123"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.email").isString());
    }

    @Test
    void refusesAnOverLongBio() throws Exception {
        String body = REGISTER.formatted(q("marko"), q("marko@example.com"), q("Lozinka123"),
                q("Marko Markovic"), q("b".repeat(501)), "[]");

        mvc.perform(register(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.bio").isString());
    }

    @Test
    void refusesAContactEntryWithNoValue() throws Exception {
        String body = REGISTER.formatted(q("marko"), q("marko@example.com"), q("Lozinka123"),
                q("Marko Markovic"), "null",
                """
                [{"type": "email", "description": "Kancelarija", "value": ""}]""");

        mvc.perform(register(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors['otherContactInfo[0].value']").isString());
    }

    @Test
    void verifyAnswers204AndSpendsTheToken() throws Exception {
        mvc.perform(post("/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "a-token"}"""))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(auth).verify("a-token");
    }

    @Test
    void verifyRefusesABlankToken() throws Exception {
        mvc.perform(post("/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "   "}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.token").isString());
    }

    @Test
    void resendAnswersTheSame204ToEveryValidAddress() throws Exception {
        mvc.perform(resend("registrovan@example.com"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mvc.perform(resend("niko@example.com"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(auth).resendVerification("registrovan@example.com");
        verify(auth).resendVerification("niko@example.com");
    }

    @Test
    void resendRefusesAMalformedAddress() throws Exception {
        mvc.perform(resend("not-an-address"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.email").isString());
    }

    @Test
    void loginAnswers200WithTheTokenAndTheUserTogether() throws Exception {
        when(auth.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("signed.jwt.value", UserDto.from(principal())));

        mvc.perform(login("marko", "Lozinka123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed.jwt.value"))
                .andExpect(jsonPath("$.user.username").value("marko"))
                .andExpect(jsonPath("$.user.actorType").value("user"));
    }

    @Test
    void loginRefusesABlankIdentifier() throws Exception {
        mvc.perform(login("   ", "Lozinka123"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.identifier").isString());
    }

    @Test
    void loginRefusesABlankPassword() throws Exception {
        mvc.perform(login("marko", ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.password").isString());
    }

    @Test
    void loginAnswers401WithNoProblemTypeOnBadCredentials() throws Exception {
        when(auth.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Wrong username or password."));

        mvc.perform(login("marko", "Pogresna123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value("/auth/login"))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void loginAnswers403WithTheTypeTheFormBranchesOn() throws Exception {
        when(auth.login(any(LoginRequest.class)))
                .thenThrow(new ForbiddenException("Verify your email address to continue.",
                        ProblemTypes.EMAIL_NOT_VERIFIED));

        mvc.perform(login("marko", "Lozinka123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/email-not-verified"));
    }

    @Test
    void passwordResetAnswersTheSame204ToEveryValidAddress() throws Exception {
        mvc.perform(passwordReset("registrovan@example.com"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mvc.perform(passwordReset("niko@example.com"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(reset).request("registrovan@example.com");
        verify(reset).request("niko@example.com");
    }

    @Test
    void passwordResetRefusesAMalformedAddress() throws Exception {
        mvc.perform(passwordReset("not-an-address"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.email").isString());

        verifyNoInteractions(reset);
    }

    @Test
    void confirmAnswers204AndHandsTheServiceBothHalves() throws Exception {
        mvc.perform(confirmReset("a-token", "NovaLozinka1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(reset).confirm("a-token", "NovaLozinka1");
    }

    @Test
    void confirmRefusesABlankToken() throws Exception {
        mvc.perform(confirmReset("   ", "NovaLozinka1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.token").isString());
    }

    @Test
    void confirmRefusesAPasswordShorterThanEight() throws Exception {
        mvc.perform(confirmReset("a-token", "Loz1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.newPassword").isString());
    }

    @Test
    void confirmRefusesAPasswordCarryingACharacterOutsideTheSet() throws Exception {
        mvc.perform(confirmReset("a-token", "Nova Lozinka1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.newPassword").isString());
    }

    @Test
    void confirmAnswers422WithTheMessageUnderErrorsToken() throws Exception {
        doThrow(UnprocessableException.on("token", "Link is invalid or has expired."))
                .when(reset).confirm(anyString(), anyString());

        mvc.perform(confirmReset("mrtav-token", "NovaLozinka1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.token").value("Link is invalid or has expired."))
                .andExpect(jsonPath("$.instance").value("/auth/password-reset/confirm"));
    }

    @Test
    void meServesEveryFieldTheClientReadsOffTheSignedInRow() throws Exception {
        signedInAs(principal());

        mvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PRINCIPAL_ID.toString()))
                .andExpect(jsonPath("$.actorType").value("user"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.username").value("marko"))
                .andExpect(jsonPath("$.fullName").value("Marko Markovic"))
                .andExpect(jsonPath("$.email").value("marko@example.com"))
                .andExpect(jsonPath("$.bio").value("Student"))
                .andExpect(jsonPath("$.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.location").value("Novi Sad"))
                .andExpect(jsonPath("$.otherContactInfo[0].value").value("office@example.com"));
    }

    /** The digest is on the row the handler maps from, so the wire has to be checked for it. */
    @Test
    void meNeverSerialisesThePasswordDigest() throws Exception {
        signedInAs(principal());

        mvc.perform(get("/auth/me"))
                .andExpect(jsonPath("$.passwordDigest").doesNotExist())
                .andExpect(jsonPath("$.emailVerifiedAt").doesNotExist());
    }

    @Test
    void logoutAnswers204AndTouchesNothing() throws Exception {
        signedInAs(principal());

        mvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verifyNoInteractions(auth);
    }

    private static void signedInAs(User user) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static User principal() {
        User user = TestFixtures.userWithId("marko", PRINCIPAL_ID);
        user.setFullName("Marko Markovic");
        user.setBio("Student");
        user.setLocation("Novi Sad");
        user.setOtherContactInfo(TestFixtures.contacts());
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }

    private static RequestBuilder login(String identifier, String password) {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"identifier": "%s", "password": "%s"}""".formatted(identifier, password));
    }

    private static RequestBuilder register(String body) {
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
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

    private static String body() {
        return body(q("marko"), q("marko@example.com"), q("Lozinka123"));
    }

    private static String body(String username, String email, String password) {
        return REGISTER.formatted(username, email, password, q("Marko Markovic"), "null", "[]");
    }

    private static String q(String value) {
        return "\"" + value + "\"";
    }
}
