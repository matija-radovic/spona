package io.github.matijaradovic.spona.feed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.post.dto.PostDto;
import io.github.matijaradovic.spona.reaction.dto.ReactionSummaryDto;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class FeedControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e4");

    private final FeedService feedService = mock(FeedService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new FeedController(feedService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aPageOfPostBlocksServesTheEnvelopeForTheAskedPage() throws Exception {
        signIn();
        Pageable asked = PageMapper.toPageable(2, 5);
        Page<FeedBlockDto> page = new PageImpl<>(List.of(FeedBlockDto.post(dto())), asked, 11);
        when(feedService.feed(caller, asked)).thenReturn(page);

        mvc.perform(get("/feed").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].kind").value("post"))
                .andExpect(jsonPath("$.content[0].post.id").value(POST_ID.toString()))
                .andExpect(jsonPath("$.content[0].post.authorFollowedByViewer").value(true))
                .andExpect(jsonPath("$.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void anAbsentPageAndSizeAskForTheFirstPageOfTheDefaultSize() throws Exception {
        signIn();
        Pageable first = PageMapper.toPageable(1, PageMapper.DEFAULT_SIZE);
        when(feedService.feed(any(), any())).thenReturn(Page.empty(first));

        mvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.last").value(true));

        verify(feedService).feed(caller, first);
    }

    private void signIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(caller, null, List.of()));
    }

    private static PostDto dto() {
        ActorRefDto author = new ActorRefDto(ActorType.USER, CALLER_ID, "", "caller", null);
        return new PostDto(POST_ID, author, "Zdravo", Instant.parse("2026-01-02T03:04:05Z"), 0,
                ReactionSummaryDto.of(List.of(), List.of()), null, true, false, false);
    }
}
