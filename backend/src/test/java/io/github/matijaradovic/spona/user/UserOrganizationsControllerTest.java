package io.github.matijaradovic.spona.user;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.organization.dto.MyOrganizationDto;
import io.github.matijaradovic.spona.organization.member.MembershipService;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.support.TestFixtures;

class UserOrganizationsControllerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-0000000002b1");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000002b2");
    private static final UUID INVITER_ID = UUID.fromString("00000000-0000-4000-8000-0000000002b3");
    private static final ActorRefDto SPONA = new ActorRefDto(ActorType.ORG, ORG_ID, "Spona", "spona", null);

    private final MembershipService memberships = mock(MembershipService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new UserController(mock(UserService.class), mock(ActorService.class), memberships))
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
    void myOrganizationsAreABareArrayWithTheOrganizationAsARefAndANullInviter() throws Exception {
        when(memberships.organizations(marko)).thenReturn(List.of(new MyOrganizationDto(SPONA,
                OrganizationRole.EDITOR, Instant.parse("2026-01-02T03:04:05Z"), Instant.parse("2026-02-03T04:05:06Z"),
                null, 4)));

        mvc.perform(get("/users/marko/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].organization.actorType").value("org"))
                .andExpect(jsonPath("$[0].organization.id").value(ORG_ID.toString()))
                .andExpect(jsonPath("$[0].organization.name").value("Spona"))
                .andExpect(jsonPath("$[0].organization.handle").value("spona"))
                .andExpect(jsonPath("$[0].organization.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$[0].role").value("EDITOR"))
                .andExpect(jsonPath("$[0].joinedAt").value("2026-01-02T03:04:05Z"))
                .andExpect(jsonPath("$[0].roleUpdatedAt").value("2026-02-03T04:05:06Z"))
                .andExpect(jsonPath("$[0].invitedBy").value(nullValue()))
                .andExpect(jsonPath("$[0].memberCount").value(4));
    }

    @Test
    void anInviterIsServedAsAUserRef() throws Exception {
        when(memberships.organizations(marko)).thenReturn(List.of(new MyOrganizationDto(SPONA,
                OrganizationRole.RECRUITER, Instant.now(), Instant.now(),
                new ActorRefDto(ActorType.USER, INVITER_ID, "", "ivana", null), 2)));

        mvc.perform(get("/users/marko/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invitedBy.actorType").value("user"))
                .andExpect(jsonPath("$[0].invitedBy.id").value(INVITER_ID.toString()))
                .andExpect(jsonPath("$[0].invitedBy.name").value(""))
                .andExpect(jsonPath("$[0].invitedBy.handle").value("ivana"));
    }

    @Test
    void anotherAccountsOrganizationsAre403AndTheServiceIsNeverAsked() throws Exception {
        mvc.perform(get("/users/jelena/organizations")).andExpect(status().isForbidden());

        verifyNoInteractions(memberships);
    }

    @Test
    void aLeaveIs204AndHandsTheSlugAndTheCallerToTheService() throws Exception {
        mvc.perform(delete("/users/marko/organizations/spona")).andExpect(status().isNoContent());

        verify(memberships).leave("spona", marko);
    }

    @Test
    void leavingOnBehalfOfAnotherAccountIs403() throws Exception {
        mvc.perform(delete("/users/jelena/organizations/spona")).andExpect(status().isForbidden());

        verifyNoInteractions(memberships);
    }

    @Test
    void theOwnersLeaveCarriesTheLastOwnerType() throws Exception {
        doThrow(new ConflictException("Transfer ownership before leaving the organization.", ProblemTypes.LAST_OWNER))
                .when(memberships).leave("spona", marko);

        mvc.perform(delete("/users/marko/organizations/spona"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/last-owner"))
                .andExpect(jsonPath("$.detail").value("Transfer ownership before leaving the organization."));
    }

    @Test
    void leavingAnOrganizationTheCallerIsNotInIs404() throws Exception {
        doThrow(new NotFoundException("You are not a member of this organization."))
                .when(memberships).leave("spona", marko);

        mvc.perform(delete("/users/marko/organizations/spona")).andExpect(status().isNotFound());
    }
}
