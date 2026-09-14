package io.github.matijaradovic.spona.reaction;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.reaction.dto.ReactionBreakdownDto;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class ReactionControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c2");

    private final ReactionService reactionService = mock(ReactionService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ReactionController(reactionService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void putIsA204ForTheCaller() throws Exception {
        signIn();

        mvc.perform(put("/reactions/post/" + TARGET_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"👍\"}"))
                .andExpect(status().isNoContent());

        verify(reactionService).react("post", TARGET_ID, caller, "👍");
    }

    @Test
    void deleteIsA204ForTheCaller() throws Exception {
        signIn();

        mvc.perform(delete("/reactions/comment/" + TARGET_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"❤️\"}"))
                .andExpect(status().isNoContent());

        verify(reactionService).unreact("comment", TARGET_ID, caller, "❤️");
    }

    @Test
    void anEmojiOutsideThePaletteIsA422UnderEmoji() throws Exception {
        signIn();
        doThrow(UnprocessableException.on("emoji", "Emoji must be one of the reaction palette."))
                .when(reactionService).react("post", TARGET_ID, caller, "🦄");

        mvc.perform(put("/reactions/post/" + TARGET_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"🦄\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.emoji").exists());
    }

    @Test
    void aGuestReadsTheWholeBreakdown() throws Exception {
        ActorRefDto reactor = new ActorRefDto(ActorType.USER, CALLER_ID, "", "caller", null);
        when(reactionService.breakdown("post", TARGET_ID)).thenReturn(new ReactionBreakdownDto(
                List.of(new ReactionBreakdownDto.Entry("👍", 1, List.of(reactor))), 1));

        mvc.perform(get("/reactions/post/" + TARGET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].emoji").value("👍"))
                .andExpect(jsonPath("$.entries[0].count").value(1))
                .andExpect(jsonPath("$.entries[0].reactors[0].actorType").value("user"))
                .andExpect(jsonPath("$.total").value(1));
    }

    private void signIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(caller, null, List.of()));
    }
}
