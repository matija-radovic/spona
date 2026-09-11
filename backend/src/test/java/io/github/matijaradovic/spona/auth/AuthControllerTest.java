package io.github.matijaradovic.spona.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.auth.dto.RegisterRequest;
import io.github.matijaradovic.spona.auth.dto.RegisterResponse;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;

class AuthControllerTest {

    private static final String REGISTER = """
            {
              "username": %s,
              "email": %s,
              "password": %s,
              "fullName": %s,
              "bio": %s,
              "avatarUrl": null,
              "location": null,
              "otherContactInfo": %s
            }""";

    private final AuthService auth = mock(AuthService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AuthController(auth))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

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

    private static RequestBuilder register(String body) {
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestBuilder resend(String email) {
        return post("/auth/verify/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}""".formatted(email));
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
