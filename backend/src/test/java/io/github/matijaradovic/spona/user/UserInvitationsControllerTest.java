package io.github.matijaradovic.spona.user;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import io.github.matijaradovic.spona.actor.ActorService;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.organization.dto.MyInvitationDto;
import io.github.matijaradovic.spona.organization.invitation.InvitationService;
import io.github.matijaradovic.spona.organization.member.MembershipService;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.support.TestFixtures;

class UserInvitationsControllerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-0000000003e1");
    private static final UUID INVITATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000003e2");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000003e3");
    private static final UUID INVITER_ID = UUID.fromString("00000000-0000-4000-8000-0000000003e4");

    private final InvitationService invitations = mock(InvitationService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new UserController(mock(UserService.class), mock(ActorService.class),
                    mock(MembershipService.class), invitations))
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
    void myInvitationsAreABareArrayWithTheOrganizationAndTheInviterAsRefs() throws Exception {
        when(invitations.mine(marko)).thenReturn(List.of(new MyInvitationDto(INVITATION_ID,
                new ActorRefDto(ActorType.ORG, ORG_ID, "Spona", "spona", null), OrganizationRole.EDITOR,
                new ActorRefDto(ActorType.USER, INVITER_ID, "Jelena Jović", "jelena", null),
                Instant.parse("2026-01-02T03:04:05Z"))));

        mvc.perform(get("/users/marko/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(INVITATION_ID.toString()))
                .andExpect(jsonPath("$[0].organization.actorType").value("org"))
                .andExpect(jsonPath("$[0].organization.handle").value("spona"))
                .andExpect(jsonPath("$[0].role").value("EDITOR"))
                .andExpect(jsonPath("$[0].invitedBy.actorType").value("user"))
                .andExpect(jsonPath("$[0].invitedBy.handle").value("jelena"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-01-02T03:04:05Z"));
    }

    @Test
    void anotherAccountsInvitationsAre403AndNothingIsRead() throws Exception {
        mvc.perform(get("/users/jelena/invitations")).andExpect(status().isForbidden());

        verifyNoInteractions(invitations);
    }
}
