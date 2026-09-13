package io.github.matijaradovic.spona.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.actor.ActorService;
import io.github.matijaradovic.spona.actor.dto.ProfileDto;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class OrganizationControllerTest {

    private static final UUID VIEWER_ID = UUID.fromString("00000000-0000-4000-8000-00000000f13e");

    private final ActorService actors = mock(ActorService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new OrganizationController(actors, mock(OrganizationService.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void servesAProfileWithoutTheThreeViewerFieldsToAGuest() throws Exception {
        when(actors.organizationProfile(eq("spona"), isNull()))
                .thenReturn(profile(ProfileDto.ViewerFields.guest()));

        mvc.perform(get("/organizations/spona"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorType").value("org"))
                .andExpect(jsonPath("$.slug").value("spona"))
                .andExpect(jsonPath("$.isFollowedByViewer").doesNotExist())
                .andExpect(jsonPath("$.viewerCanEdit").doesNotExist())
                .andExpect(jsonPath("$.viewerCanManageJobPostings").doesNotExist());
    }

    @Test
    void carriesTheTwoAuthoritiesSeparatelyForARecruiter() throws Exception {
        when(actors.organizationProfile(eq("spona"), any(User.class)))
                .thenReturn(profile(new ProfileDto.ViewerFields(false, false, true)));
        signedInAs(TestFixtures.userWithId("viewer", VIEWER_ID));

        mvc.perform(get("/organizations/spona"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanManageJobPostings").value(true));
    }

    @Test
    void handsTheAuthenticatedCallerToTheServiceAsTheViewer() throws Exception {
        User viewer = TestFixtures.userWithId("viewer", VIEWER_ID);
        when(actors.organizationProfile(eq("spona"), any(User.class)))
                .thenReturn(profile(new ProfileDto.ViewerFields(false, false, false)));
        signedInAs(viewer);

        mvc.perform(get("/organizations/spona")).andExpect(status().isOk());

        verify(actors).organizationProfile("spona", viewer);
    }

    @Test
    void answers404ForASlugThatIsNobody() throws Exception {
        when(actors.organizationProfile(eq("nepostojeci"), isNull()))
                .thenThrow(new NotFoundException("Profile not found."));

        mvc.perform(get("/organizations/nepostojeci"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Profile not found."));
    }

    @Test
    void hasNoFollowingCountBecauseOnlyAPersonFollows() throws Exception {
        when(actors.organizationProfile(eq("spona"), isNull()))
                .thenReturn(profile(ProfileDto.ViewerFields.guest()));

        mvc.perform(get("/organizations/spona"))
                .andExpect(jsonPath("$.followers").value(96))
                .andExpect(jsonPath("$.following").doesNotExist());
    }

    @Test
    void neverSerialisesTheLegalNameOrThePrivacyFlag() throws Exception {
        when(actors.organizationProfile(eq("spona"), isNull()))
                .thenReturn(profile(ProfileDto.ViewerFields.guest()));

        mvc.perform(get("/organizations/spona"))
                .andExpect(jsonPath("$.legalName").doesNotExist())
                .andExpect(jsonPath("$.showJobPostings").doesNotExist());
    }

    private static ProfileDto profile(ProfileDto.ViewerFields viewer) {
        Organization subject = TestFixtures.organization("spona");
        ReflectionTestUtils.setField(subject, "id", UUID.randomUUID());
        return ProfileDto.from(subject, 96, viewer);
    }

    private static void signedInAs(User user) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
