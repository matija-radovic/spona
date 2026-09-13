package io.github.matijaradovic.spona.actor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.actor.privacy.PrivacyService;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class ActorControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c4");

    private final PrivacyService privacy = mock(PrivacyService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ActorController(privacy))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);

    @BeforeEach
    void signIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(caller, null, List.of()));
    }

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aPrivacyWriteIs204AndHandsOverTheCaller() throws Exception {
        mvc.perform(put("/actors/" + ORG_ID + "/privacy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"showJobPostings\":false}"))
                .andExpect(status().isNoContent());

        verify(privacy).update(ORG_ID, caller, new PrivacyDto(false));
    }

    @Test
    void aMissingFlagIs422() throws Exception {
        mvc.perform(put("/actors/" + ORG_ID + "/privacy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.showJobPostings").exists());

        verifyNoInteractions(privacy);
    }

    @Test
    void anIdThatIsNotAUuidIs400() throws Exception {
        mvc.perform(put("/actors/nije-uuid/privacy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"showJobPostings\":true}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(privacy);
    }

    @Test
    void aRefusalIs403() throws Exception {
        doThrow(new ForbiddenException("You may not change these privacy settings."))
                .when(privacy).update(eq(ORG_ID), eq(caller), any());

        mvc.perform(put("/actors/" + ORG_ID + "/privacy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"showJobPostings\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownActorIs404() throws Exception {
        doThrow(new NotFoundException("Actor not found."))
                .when(privacy).update(eq(ORG_ID), eq(caller), any());

        mvc.perform(put("/actors/" + ORG_ID + "/privacy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"showJobPostings\":true}"))
                .andExpect(status().isNotFound());
    }
}
