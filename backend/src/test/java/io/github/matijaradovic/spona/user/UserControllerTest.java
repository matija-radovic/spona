package io.github.matijaradovic.spona.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.actor.ActorService;
import io.github.matijaradovic.spona.actor.dto.ProfileDto;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.organization.invitation.InvitationService;
import io.github.matijaradovic.spona.organization.member.MembershipService;
import io.github.matijaradovic.spona.support.TestFixtures;

class UserControllerTest {

    private static final UUID VIEWER_ID = UUID.fromString("00000000-0000-4000-8000-00000000f13d");

    private final UserService service = mock(UserService.class);
    private final ActorService actors = mock(ActorService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new UserController(service, actors, mock(MembershipService.class),
                    mock(InvitationService.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void answersTrueForAFreeHandle() throws Exception {
        when(service.isUsernameAvailable("marko")).thenReturn(true);

        mvc.perform(get("/users").param("username", "marko"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void answersFalseForAHandleThatCannotBeRegistered() throws Exception {
        when(service.isUsernameAvailable("admin")).thenReturn(false);

        mvc.perform(get("/users").param("username", "admin"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void refusesTheCallWithNoHandleToCheck() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isBadRequest());
    }

    @Test
    void servesAProfileWithoutTheThreeViewerFieldsToAGuest() throws Exception {
        when(actors.userProfile(eq("marko"), isNull()))
                .thenReturn(profile(ProfileDto.ViewerFields.guest()));

        mvc.perform(get("/users/marko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorType").value("user"))
                .andExpect(jsonPath("$.username").value("marko"))
                .andExpect(jsonPath("$.isFollowedByViewer").doesNotExist())
                .andExpect(jsonPath("$.viewerCanEdit").doesNotExist())
                .andExpect(jsonPath("$.viewerCanManageJobPostings").doesNotExist());
    }

    @Test
    void spellsTheViewerFieldsExactlyAsTheClientReadsThem() throws Exception {
        when(actors.userProfile(eq("marko"), any(User.class)))
                .thenReturn(profile(new ProfileDto.ViewerFields(true, false, false)));
        signedInAs(TestFixtures.userWithId("viewer", VIEWER_ID));

        mvc.perform(get("/users/marko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFollowedByViewer").value(true))
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanManageJobPostings").value(false));
    }

    @Test
    void handsTheAuthenticatedCallerToTheServiceAsTheViewer() throws Exception {
        User viewer = TestFixtures.userWithId("viewer", VIEWER_ID);
        when(actors.userProfile(eq("marko"), any(User.class)))
                .thenReturn(profile(new ProfileDto.ViewerFields(false, false, false)));
        signedInAs(viewer);

        mvc.perform(get("/users/marko")).andExpect(status().isOk());

        verify(actors).userProfile("marko", viewer);
    }

    @Test
    void answers404ForAHandleThatIsNobody() throws Exception {
        when(actors.userProfile(eq("nepostojeci"), isNull()))
                .thenThrow(new NotFoundException("Profile not found."));

        mvc.perform(get("/users/nepostojeci"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Profile not found."));
    }

    @Test
    void neverSerialisesTheAddress() throws Exception {
        when(actors.userProfile(eq("marko"), isNull()))
                .thenReturn(profile(ProfileDto.ViewerFields.guest()));

        mvc.perform(get("/users/marko"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    private static ProfileDto profile(ProfileDto.ViewerFields viewer) {
        User subject = TestFixtures.userWithId("marko", UUID.randomUUID());
        subject.setFullName("Marko Markovic");
        return ProfileDto.from(subject, 42, 30, viewer);
    }

    private static void signedInAs(User user) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
