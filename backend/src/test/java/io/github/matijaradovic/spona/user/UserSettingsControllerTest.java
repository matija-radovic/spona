package io.github.matijaradovic.spona.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.actor.ActorService;
import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.organization.member.MembershipService;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.dto.ChangeEmailRequest;
import io.github.matijaradovic.spona.user.dto.ChangePasswordRequest;
import io.github.matijaradovic.spona.user.dto.UserSettingsDto;

class UserSettingsControllerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final String PROFILE_BODY = """
            {"fullName":"Marko","bio":null,"avatar":null,"location":null,"otherContactInfo":[]}""";

    private final UserService service = mock(UserService.class);
    private final ActorService actors = mock(ActorService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new UserController(service, actors, mock(MembershipService.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    private final User marko = TestFixtures.userWithId("marko", ID);

    @BeforeEach
    void signIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(marko, null, List.of()));
    }

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void servesTheOwnersSettingsWithPrivacyAndWithoutTheProfileOnlyFields() throws Exception {
        when(service.settings(marko)).thenReturn(settings("marko", false));

        mvc.perform(get("/users/marko/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.username").value("marko"))
                .andExpect(jsonPath("$.email").value("marko@example.com"))
                .andExpect(jsonPath("$.otherContactInfo.length()").value(2))
                .andExpect(jsonPath("$.privacy.showJobPostings").value(false))
                .andExpect(jsonPath("$.actorType").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.passwordDigest").doesNotExist());
    }

    @Test
    void theSettingsOfAnotherHandleAre403() throws Exception {
        mvc.perform(get("/users/petar/settings")).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void aProfileUpdateForAnotherHandleIs403() throws Exception {
        mvc.perform(json(put("/users/petar"), PROFILE_BODY)).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void aRenameOfAnotherHandleIs403() throws Exception {
        mvc.perform(json(put("/users/petar/username"), "{\"username\":\"novo\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void anEmailChangeForAnotherHandleIs403() throws Exception {
        mvc.perform(json(post("/users/petar/email"), "{\"email\":\"a@primer.rs\",\"currentPassword\":\"x\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void aPasswordChangeForAnotherHandleIs403() throws Exception {
        mvc.perform(json(put("/users/petar/password"),
                "{\"currentPassword\":\"x\",\"newPassword\":\"NovaLozinka1\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void deletingAnotherAccountIs403() throws Exception {
        mvc.perform(json(delete("/users/petar"), "{\"password\":\"x\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void aProfileUpdateAnswersTheWholeSettings() throws Exception {
        when(service.updateProfile(eq(marko), any())).thenReturn(settings("marko", true));

        mvc.perform(json(put("/users/marko"), PROFILE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("marko"))
                .andExpect(jsonPath("$.privacy.showJobPostings").value(true));
    }

    @Test
    void aBioOverTheBoundIs422UnderBio() throws Exception {
        String body = "{\"fullName\":\"Marko\",\"bio\":\"" + "a".repeat(501) + "\",\"otherContactInfo\":[]}";

        mvc.perform(json(put("/users/marko"), body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.bio").exists());

        verifyNoInteractions(service);
    }

    @Test
    void sixteenContactEntriesAre422() throws Exception {
        String entries = IntStream.range(0, 16)
                .mapToObj(i -> "{\"type\":\"email\",\"description\":\"\",\"value\":\"k" + i + "@primer.rs\"}")
                .collect(Collectors.joining(","));

        mvc.perform(json(put("/users/marko"), "{\"fullName\":\"Marko\",\"otherContactInfo\":[" + entries + "]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.otherContactInfo").exists());
    }

    @Test
    void aRenameAnswersTheNewHandle() throws Exception {
        when(service.changeUsername(marko, "petar")).thenReturn(settings("petar", true));

        mvc.perform(json(put("/users/marko/username"), "{\"username\":\"petar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("petar"));
    }

    @Test
    void aMalformedHandleIs422UnderUsername() throws Exception {
        mvc.perform(json(put("/users/marko/username"), "{\"username\":\"Marko Markovic\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.username").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aTakenHandleCarriesItsProblemType() throws Exception {
        when(service.changeUsername(marko, "zauzeto"))
                .thenThrow(new ConflictException("That username is taken.", ProblemTypes.USERNAME_TAKEN));

        mvc.perform(json(put("/users/marko/username"), "{\"username\":\"zauzeto\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/username-taken"));
    }

    @Test
    void anEmailChangeIs204() throws Exception {
        mvc.perform(json(post("/users/marko/email"),
                "{\"email\":\"novi@primer.rs\",\"currentPassword\":\"Lozinka123\"}"))
                .andExpect(status().isNoContent());

        verify(service).requestEmailChange(marko, new ChangeEmailRequest("novi@primer.rs", "Lozinka123"));
    }

    @Test
    void aMalformedAddressIs422UnderEmail() throws Exception {
        mvc.perform(json(post("/users/marko/email"), "{\"email\":\"nije-adresa\",\"currentPassword\":\"x\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.email").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aWrongPasswordIs403AndNever401() throws Exception {
        doThrow(new ForbiddenException("Wrong password."))
                .when(service).requestEmailChange(eq(marko), any());

        mvc.perform(json(post("/users/marko/email"), "{\"email\":\"novi@primer.rs\",\"currentPassword\":\"pogresna\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Wrong password."));
    }

    @Test
    void aPasswordChangeIs204() throws Exception {
        mvc.perform(json(put("/users/marko/password"),
                "{\"currentPassword\":\"Lozinka123\",\"newPassword\":\"NovaLozinka1\"}"))
                .andExpect(status().isNoContent());

        verify(service).changePassword(marko, new ChangePasswordRequest("Lozinka123", "NovaLozinka1"));
    }

    @Test
    void aShortNewPasswordIs422BeforeAnythingIsHashed() throws Exception {
        mvc.perform(json(put("/users/marko/password"), "{\"currentPassword\":\"Lozinka123\",\"newPassword\":\"kratka\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.newPassword").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aNewPasswordWithACharacterOutsideTheSetIs422() throws Exception {
        mvc.perform(json(put("/users/marko/password"),
                "{\"currentPassword\":\"Lozinka123\",\"newPassword\":\"nova lozinka\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.newPassword").exists());

        verifyNoInteractions(service);
    }

    @Test
    void deletingTheAccountReadsThePasswordFromTheBody() throws Exception {
        mvc.perform(json(delete("/users/marko"), "{\"password\":\"Lozinka123\"}"))
                .andExpect(status().isNoContent());

        verify(service).deleteAccount(marko, "Lozinka123");
    }

    @Test
    void deletingWithABlankPasswordIs422() throws Exception {
        mvc.perform(json(delete("/users/marko"), "{\"password\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(service);
    }

    @Test
    void deletingWithNoBodyIsRefused() throws Exception {
        mvc.perform(delete("/users/marko")).andExpect(status().is4xxClientError());

        verifyNoInteractions(service);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static UserSettingsDto settings(String username, boolean showJobPostings) {
        return new UserSettingsDto(ID, username, "marko@example.com", "Marko", null, null, "Beograd",
                TestFixtures.contacts(), new PrivacyDto(showJobPostings));
    }
}
