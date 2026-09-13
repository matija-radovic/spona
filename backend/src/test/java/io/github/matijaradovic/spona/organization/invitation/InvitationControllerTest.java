package io.github.matijaradovic.spona.organization.invitation;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class InvitationControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000003d1");
    private static final UUID INVITATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000003d2");
    private static final String ACCEPT_PATH = "/invitations/" + INVITATION_ID + "/accept";
    private static final String DECLINE_PATH = "/invitations/" + INVITATION_ID + "/decline";

    private final InvitationService invitations = mock(InvitationService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new InvitationController(invitations))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    private final User marko = TestFixtures.userWithId("marko", CALLER_ID);

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
    void acceptIs204AndHandsTheIdAndTheCallerToTheService() throws Exception {
        mvc.perform(post(ACCEPT_PATH)).andExpect(status().isNoContent());

        verify(invitations).accept(INVITATION_ID, marko);
    }

    @Test
    void declineIs204AndHandsTheIdAndTheCallerToTheService() throws Exception {
        mvc.perform(post(DECLINE_PATH)).andExpect(status().isNoContent());

        verify(invitations).decline(INVITATION_ID, marko);
    }

    @Test
    void someoneElsesInvitationIs403InvitationNotYours() throws Exception {
        doThrow(new ForbiddenException("This invitation is addressed to someone else.",
                ProblemTypes.INVITATION_NOT_YOURS)).when(invitations).accept(INVITATION_ID, marko);

        mvc.perform(post(ACCEPT_PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/invitation-not-yours"));
    }

    @Test
    void anIdThatIsNobodyIs404() throws Exception {
        doThrow(new NotFoundException("Invitation not found.")).when(invitations).decline(INVITATION_ID, marko);

        mvc.perform(post(DECLINE_PATH)).andExpect(status().isNotFound());
    }

    @Test
    void acceptingAsAMemberIs409AlreadyMember() throws Exception {
        doThrow(new ConflictException("You are already a member of this organization.",
                ProblemTypes.ALREADY_MEMBER)).when(invitations).accept(INVITATION_ID, marko);

        mvc.perform(post(ACCEPT_PATH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/already-member"));
    }

    @Test
    void aRowThatIsNoLongerWaitingIs409WithNoType() throws Exception {
        doThrow(new ConflictException("This invitation is no longer waiting."))
                .when(invitations).decline(INVITATION_ID, marko);

        mvc.perform(post(DECLINE_PATH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void anIdThatIsNotAUuidIs400AndNeverReachesTheService() throws Exception {
        mvc.perform(post("/invitations/my-inv-1/accept")).andExpect(status().isBadRequest());

        verifyNoInteractions(invitations);
    }
}
