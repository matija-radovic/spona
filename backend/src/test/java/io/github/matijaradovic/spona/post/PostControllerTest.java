package io.github.matijaradovic.spona.post;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import java.time.Instant;
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
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.post.dto.CreatePostRequest;
import io.github.matijaradovic.spona.post.dto.PostDto;
import io.github.matijaradovic.spona.post.dto.UpdatePostRequest;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class PostControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-4000-8000-0000000000f2");

    private final PostService postService = mock(PostService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new PostController(postService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aGuestReadsAPostWithoutViewerFields() throws Exception {
        when(postService.post(POST_ID, null)).thenReturn(dto("Zdravo", null));

        mvc.perform(get("/posts/" + POST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(POST_ID.toString()))
                .andExpect(jsonPath("$.author.actorType").value("user"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-02T03:04:05Z"))
                .andExpect(jsonPath("$.reactions.total").value(0))
                .andExpect(jsonPath("$.authorBio").value("Bio"))
                .andExpect(jsonPath("$.authorFollowedByViewer").doesNotExist())
                .andExpect(jsonPath("$.viewerCanEdit").doesNotExist())
                .andExpect(jsonPath("$.viewerCanDelete").doesNotExist());
    }

    @Test
    void createIsA201ForTheCaller() throws Exception {
        signIn();
        when(postService.create(caller, new CreatePostRequest("Zdravo", CALLER_ID))).thenReturn(dto("Zdravo", true));

        mvc.perform(post("/posts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Zdravo\",\"createdForId\":\"" + CALLER_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Zdravo"))
                .andExpect(jsonPath("$.authorFollowedByViewer").value(false))
                .andExpect(jsonPath("$.viewerCanEdit").value(true))
                .andExpect(jsonPath("$.viewerCanDelete").value(true));
    }

    @Test
    void createWithoutAnActorIs422BeforeTheService() throws Exception {
        signIn();

        mvc.perform(post("/posts").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Zdravo\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.createdForId").exists());

        verifyNoInteractions(postService);
    }

    @Test
    void anOverlongPostIs422UnderContent() throws Exception {
        signIn();
        when(postService.create(eq(caller), any()))
                .thenThrow(UnprocessableException.on("content", ErrorCode.POST_CONTENT_TOO_LONG.with(5000)));

        mvc.perform(post("/posts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\",\"createdForId\":\"" + CALLER_ID + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.content.detail").value("At most 5000 characters."));
    }

    @Test
    void updateIsA200WithTheEditedPost() throws Exception {
        signIn();
        when(postService.update(POST_ID, caller, new UpdatePostRequest("Novo"))).thenReturn(dto("Novo", true));

        mvc.perform(put("/posts/" + POST_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Novo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Novo"));
    }

    @Test
    void aRefusedUpdateIs403() throws Exception {
        signIn();
        when(postService.update(eq(POST_ID), eq(caller), any()))
                .thenThrow(new ForbiddenException(ErrorCode.POST_NOT_EDITABLE));

        mvc.perform(put("/posts/" + POST_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Novo\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteIsA204() throws Exception {
        signIn();

        mvc.perform(delete("/posts/" + POST_ID)).andExpect(status().isNoContent());

        verify(postService).delete(POST_ID, caller);
    }

    private void signIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(caller, null, List.of()));
    }

    private static PostDto dto(String content, Boolean canManage) {
        ActorRefDto author = new ActorRefDto(ActorType.USER, CALLER_ID, "", "caller", null);
        return new PostDto(POST_ID, author, content, Instant.parse("2026-01-02T03:04:05Z"), 0,
                ReactionSummaryDto.of(List.of(), null), "Bio", canManage == null ? null : false, canManage, canManage);
    }
}
