package io.github.matijaradovic.spona.comment;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.comment.dto.CommentContextDto;
import io.github.matijaradovic.spona.comment.dto.CommentDto;
import io.github.matijaradovic.spona.comment.dto.CreateCommentRequest;
import io.github.matijaradovic.spona.comment.dto.DeleteCommentResultDto;
import io.github.matijaradovic.spona.comment.dto.UpdateCommentRequest;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class CommentControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    private static final UUID COMMENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    private static final UUID PARENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c4");

    private final CommentService commentService = mock(CommentService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new CommentController(commentService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aGuestReadsTheThreadUnderTheDefaultSort() throws Exception {
        when(commentService.thread(eq(POST_ID), eq("top"), isNull(), eq(PageRequest.of(0, 10)), isNull()))
                .thenReturn(new PageImpl<>(List.of(dto(null, null)), PageRequest.of(0, 10), 1));

        mvc.perform(get("/posts/" + POST_ID + "/comments").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(COMMENT_ID.toString()))
                .andExpect(jsonPath("$.content[0].postId").value(POST_ID.toString()))
                .andExpect(jsonPath("$.content[0].parentId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].responseCount").value(0))
                .andExpect(jsonPath("$.content[0].viewerCanEdit").doesNotExist())
                .andExpect(jsonPath("$.content[0].viewerCanDelete").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void sortAndExcludeReachTheService() throws Exception {
        when(commentService.thread(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/posts/" + POST_ID + "/comments").param("sort", "new").param("exclude", PARENT_ID.toString()))
                .andExpect(status().isOk());

        verify(commentService).thread(eq(POST_ID), eq("new"), eq(PARENT_ID), any(), isNull());
    }

    @Test
    void contextOneServesTheContextShape() throws Exception {
        when(commentService.context(COMMENT_ID, null, null))
                .thenReturn(new CommentContextDto(dto(null, null), null, 0, 3));

        mvc.perform(get("/comments/" + COMMENT_ID).param("context", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment.id").value(COMMENT_ID.toString()))
                .andExpect(jsonPath("$.parent").value(nullValue()))
                .andExpect(jsonPath("$.siblingIndex").value(0))
                .andExpect(jsonPath("$.siblingTotal").value(3));

        verify(commentService, never()).comment(any(), any(), any());
    }

    @Test
    void withoutContextTheCommentIsServedAlone() throws Exception {
        when(commentService.comment(COMMENT_ID, null, null)).thenReturn(dto(PARENT_ID, null));

        mvc.perform(get("/comments/" + COMMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COMMENT_ID.toString()))
                .andExpect(jsonPath("$.parentId").value(PARENT_ID.toString()))
                .andExpect(jsonPath("$.siblingIndex").doesNotExist());
    }

    @Test
    void thePostScopedFormsHandTheServiceThePost() throws Exception {
        when(commentService.comment(COMMENT_ID, POST_ID, null)).thenReturn(dto(null, null));
        when(commentService.responses(eq(COMMENT_ID), eq(POST_ID), any(), isNull()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/posts/" + POST_ID + "/comments/" + COMMENT_ID)).andExpect(status().isOk());
        mvc.perform(get("/posts/" + POST_ID + "/comments/" + COMMENT_ID + "/responses")).andExpect(status().isOk());

        verify(commentService).comment(COMMENT_ID, POST_ID, null);
        verify(commentService).responses(eq(COMMENT_ID), eq(POST_ID), any(), isNull());
    }

    @Test
    void createIsA201ForTheCaller() throws Exception {
        signIn();
        when(commentService.create(POST_ID, caller, new CreateCommentRequest("Zdravo", CALLER_ID, PARENT_ID)))
                .thenReturn(dto(PARENT_ID, true));

        mvc.perform(post("/posts/" + POST_ID + "/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Zdravo\",\"createdForId\":\"" + CALLER_ID
                                + "\",\"parentId\":\"" + PARENT_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(PARENT_ID.toString()))
                .andExpect(jsonPath("$.viewerCanEdit").value(true))
                .andExpect(jsonPath("$.viewerCanDelete").value(true));
    }

    @Test
    void createWithoutAnActorIs422BeforeTheService() throws Exception {
        signIn();

        mvc.perform(post("/posts/" + POST_ID + "/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Zdravo\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.createdForId").exists());

        verifyNoInteractions(commentService);
    }

    @Test
    void aReplyToAReplyIs422UnderParentId() throws Exception {
        signIn();
        when(commentService.create(eq(POST_ID), eq(caller), any()))
                .thenThrow(UnprocessableException.on("parentId", "A reply cannot be replied to."));

        mvc.perform(post("/posts/" + POST_ID + "/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Zdravo\",\"createdForId\":\"" + CALLER_ID
                                + "\",\"parentId\":\"" + PARENT_ID + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.parentId").value("A reply cannot be replied to."));
    }

    @Test
    void updateIsA200WithTheEditedComment() throws Exception {
        signIn();
        when(commentService.update(COMMENT_ID, caller, new UpdateCommentRequest("Zdravo"))).thenReturn(dto(null, true));

        mvc.perform(put("/comments/" + COMMENT_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Zdravo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Zdravo"));
    }

    @Test
    void deleteAnswersHowManyRowsWent() throws Exception {
        signIn();
        when(commentService.delete(COMMENT_ID, caller)).thenReturn(new DeleteCommentResultDto(3));

        mvc.perform(delete("/comments/" + COMMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(3));
    }

    private void signIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(caller, null, List.of()));
    }

    private static CommentDto dto(UUID parentId, Boolean canManage) {
        ActorRefDto author = new ActorRefDto(ActorType.USER, CALLER_ID, "", "caller", null);
        return new CommentDto(COMMENT_ID, POST_ID, parentId, author, "Zdravo", Instant.parse("2026-01-02T03:04:05Z"),
                ReactionSummaryDto.of(List.of(), null), 0, canManage, canManage);
    }
}
