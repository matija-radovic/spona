package io.github.matijaradovic.spona.actor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.actor.activity.ActivityService;
import io.github.matijaradovic.spona.actor.dto.FollowActorDto;
import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.actor.follow.FollowService;
import io.github.matijaradovic.spona.actor.privacy.PrivacyService;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.common.error.BadRequestException;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.job.JobService;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class ActorControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c4");

    private final PrivacyService privacy = mock(PrivacyService.class);
    private final ActorService actorsService = mock(ActorService.class);
    private final FollowService followService = mock(FollowService.class);
    private final ActivityService activityService = mock(ActivityService.class);
    private final JobService jobService = mock(JobService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ActorController(privacy, actorsService, followService, activityService, jobService))
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
    void followIsA204ForTheCaller() throws Exception {
        mvc.perform(put("/actors/" + ORG_ID + "/follow"))
                .andExpect(status().isNoContent());

        verify(followService).follow(ORG_ID, caller);
    }

    @Test
    void unfollowIsA204ForTheCaller() throws Exception {
        mvc.perform(delete("/actors/" + ORG_ID + "/follow"))
                .andExpect(status().isNoContent());

        verify(followService).unfollow(ORG_ID, caller);
    }

    @Test
    void searchDefaultsToAnEmptyQueryOnTheFirstPageOfTwenty() throws Exception {
        Pageable first = PageRequest.of(0, 20);
        when(actorsService.search("", "org", first, caller)).thenReturn(new PageImpl<>(List.of(), first, 0));

        mvc.perform(get("/actors").param("type", "org"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void searchWithoutATypeIsA400() throws Exception {
        mvc.perform(get("/actors").param("q", "ana"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(actorsService);
    }

    @Test
    void followersPassQAndThePageAndServeTheCallersEdge() throws Exception {
        Pageable second = PageRequest.of(1, 5);
        FollowActorDto row = FollowActorDto.from(ActorRefDto.from(TestFixtures.userWithId("ana", CALLER_ID)), true);
        when(followService.followers(ORG_ID, "ana", second, caller))
                .thenReturn(new PageImpl<>(List.of(row), second, 6));

        mvc.perform(get("/actors/" + ORG_ID + "/followers").param("q", "ana").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.content[0].handle").value("ana"))
                .andExpect(jsonPath("$.content[0].actorType").value("user"))
                .andExpect(jsonPath("$.content[0].isFollowedByViewer").value(true));
    }

    @Test
    void followingOfAnOrganizationIsA404() throws Exception {
        when(followService.following(eq(ORG_ID), eq(""), any(), eq(caller)))
                .thenThrow(new NotFoundException(ErrorCode.ORGANIZATION_FOLLOWS_NOBODY));

        mvc.perform(get("/actors/" + ORG_ID + "/following"))
                .andExpect(status().isNotFound());
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
        doThrow(new ForbiddenException(ErrorCode.PRIVACY_NOT_YOURS))
                .when(privacy).update(eq(ORG_ID), eq(caller), any());

        mvc.perform(put("/actors/" + ORG_ID + "/privacy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"showJobPostings\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownActorIs404() throws Exception {
        doThrow(new NotFoundException(ErrorCode.ACTOR_NOT_FOUND))
                .when(privacy).update(eq(ORG_ID), eq(caller), any());

        mvc.perform(put("/actors/" + ORG_ID + "/privacy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"showJobPostings\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void activityDefaultsToKindAllOnTheFirstPageOfTwenty() throws Exception {
        Pageable first = PageRequest.of(0, 20);
        when(activityService.activity(ORG_ID, "all", first, caller)).thenReturn(new PageImpl<>(List.of(), first, 0));

        mvc.perform(get("/actors/" + ORG_ID + "/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void activityPassesTheKindAndThePage() throws Exception {
        Pageable second = PageRequest.of(1, 5);
        when(activityService.activity(ORG_ID, "posts", second, caller))
                .thenReturn(new PageImpl<>(List.of(), second, 0));

        mvc.perform(get("/actors/" + ORG_ID + "/activity")
                .param("kind", "posts").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(activityService).activity(ORG_ID, "posts", second, caller);
    }

    @Test
    void activityOfAnUnknownActorIs404() throws Exception {
        when(activityService.activity(eq(ORG_ID), any(), any(), eq(caller)))
                .thenThrow(new NotFoundException(ErrorCode.ACTOR_NOT_FOUND));

        mvc.perform(get("/actors/" + ORG_ID + "/activity")).andExpect(status().isNotFound());
    }

    @Test
    void anInvalidKindIs422() throws Exception {
        when(activityService.activity(eq(ORG_ID), eq("bogus"), any(), eq(caller)))
                .thenThrow(UnprocessableException.on("kind", ErrorCode.UNKNOWN_ACTIVITY_KIND));

        mvc.perform(get("/actors/" + ORG_ID + "/activity").param("kind", "bogus"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.kind").exists());
    }

    @Test
    void postingsAskForNothingOnTheFirstPageOfTwenty() throws Exception {
        Pageable first = PageRequest.of(0, 20);
        when(jobService.postings(ORG_ID, null, null, first, caller)).thenReturn(new PageImpl<>(List.of(), first, 0));

        mvc.perform(get("/actors/" + ORG_ID + "/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void postingsPassTheStatusTheSortAndThePage() throws Exception {
        Pageable second = PageRequest.of(1, 5);
        when(jobService.postings(ORG_ID, "DRAFT", "updated_asc", second, caller))
                .thenReturn(new PageImpl<>(List.of(), second, 0));

        mvc.perform(get("/actors/" + ORG_ID + "/jobs")
                .param("status", "DRAFT").param("sort", "updated_asc").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(jobService).postings(ORG_ID, "DRAFT", "updated_asc", second, caller);
    }

    @Test
    void aRefusedPostingsParameterIs400WithNoFieldErrors() throws Exception {
        when(jobService.postings(eq(ORG_ID), eq("DRAFT"), any(), any(), eq(caller)))
                .thenThrow(new BadRequestException(ErrorCode.JOB_MANAGER_ONLY_QUERY));

        mvc.perform(get("/actors/" + ORG_ID + "/jobs").param("status", "DRAFT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
