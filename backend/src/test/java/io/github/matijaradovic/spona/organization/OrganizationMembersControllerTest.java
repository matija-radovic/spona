package io.github.matijaradovic.spona.organization;

import static org.hamcrest.Matchers.nullValue;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.actor.ActorService;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.organization.dto.OrgMemberDto;
import io.github.matijaradovic.spona.organization.member.MembershipService;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class OrganizationMembersControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000001a1");
    private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-4000-8000-0000000001a2");
    private static final String MEMBER_PATH = "/organizations/spona/members/" + MEMBER_ID;

    private final MembershipService memberships = mock(MembershipService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new OrganizationController(mock(ActorService.class), mock(OrganizationService.class),
                    memberships))
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
    void theRosterIsABareArrayOfMembersWithTheirRoleAndJoinDate() throws Exception {
        when(memberships.roster("spona", marko)).thenReturn(List.of(new OrgMemberDto(MEMBER_ID, "jelena",
                "Jelena Jović", null, OrganizationRole.ADMIN, Instant.parse("2026-01-02T03:04:05Z"))));

        mvc.perform(get("/organizations/spona/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(MEMBER_ID.toString()))
                .andExpect(jsonPath("$[0].username").value("jelena"))
                .andExpect(jsonPath("$[0].fullName").value("Jelena Jović"))
                .andExpect(jsonPath("$[0].avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].joinedAt").value("2026-01-02T03:04:05Z"))
                .andExpect(jsonPath("$[0].assignedAt").doesNotExist());
    }

    @Test
    void theRosterOfAnOrganizationTheCallerIsNotInIs403() throws Exception {
        when(memberships.roster("spona", marko))
                .thenThrow(new ForbiddenException("You are not a member of this organization."));

        mvc.perform(get("/organizations/spona/members")).andExpect(status().isForbidden());
    }

    @Test
    void theRosterOfASlugThatIsNobodyIs404() throws Exception {
        when(memberships.roster("nepostojeci", marko)).thenThrow(new NotFoundException("Organization not found."));

        mvc.perform(get("/organizations/nepostojeci/members"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Organization not found."));
    }

    @Test
    void aRoleChangeIs204AndHandsTheSlugTheCallerTheMemberAndTheRoleToTheService() throws Exception {
        mvc.perform(json(put(MEMBER_PATH), "{\"role\":\"RECRUITER\"}")).andExpect(status().isNoContent());

        verify(memberships).changeRole("spona", marko, MEMBER_ID, OrganizationRole.RECRUITER);
    }

    @Test
    void aRoleChangeWithoutARoleIs422UnderRole() throws Exception {
        mvc.perform(json(put(MEMBER_PATH), "{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.role").exists());

        verifyNoInteractions(memberships);
    }

    @Test
    void aRoleThatIsNoRoleIsRefused() throws Exception {
        mvc.perform(json(put(MEMBER_PATH), "{\"role\":\"SUPERUSER\"}")).andExpect(status().is4xxClientError());

        verifyNoInteractions(memberships);
    }

    @Test
    void aMemberSegmentThatIsNotAnIdIsRefused() throws Exception {
        mvc.perform(json(put("/organizations/spona/members/jelena"), "{\"role\":\"EDITOR\"}"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(memberships);
    }

    @Test
    void makingSomebodyTheOwnerIs422UnderRoleWithItsMessage() throws Exception {
        doThrow(UnprocessableException.on("role", "Ownership moves only by transfer."))
                .when(memberships).changeRole("spona", marko, MEMBER_ID, OrganizationRole.OWNER);

        mvc.perform(json(put(MEMBER_PATH), "{\"role\":\"OWNER\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.role").value("Ownership moves only by transfer."));
    }

    @Test
    void aRoleChangeAimedAtTheOwnerCarriesTheLastOwnerType() throws Exception {
        doThrow(new ConflictException("The owner keeps their role until ownership is transferred.",
                ProblemTypes.LAST_OWNER))
                .when(memberships).changeRole("spona", marko, MEMBER_ID, OrganizationRole.EDITOR);

        mvc.perform(json(put(MEMBER_PATH), "{\"role\":\"EDITOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/last-owner"));
    }

    @Test
    void aRoleChangeByAMemberWhoMayNotIs403() throws Exception {
        doThrow(new ForbiddenException("Only the owner or an admin may change members."))
                .when(memberships).changeRole("spona", marko, MEMBER_ID, OrganizationRole.EDITOR);

        mvc.perform(json(put(MEMBER_PATH), "{\"role\":\"EDITOR\"}")).andExpect(status().isForbidden());
    }

    @Test
    void aRemovalIs204AndTakesNoBody() throws Exception {
        mvc.perform(delete(MEMBER_PATH)).andExpect(status().isNoContent());

        verify(memberships).remove("spona", marko, MEMBER_ID);
    }

    @Test
    void aRemovalOfTheOwnerCarriesTheLastOwnerType() throws Exception {
        doThrow(new ConflictException("The owner keeps their role until ownership is transferred.",
                ProblemTypes.LAST_OWNER))
                .when(memberships).remove("spona", marko, MEMBER_ID);

        mvc.perform(delete(MEMBER_PATH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/last-owner"));
    }

    @Test
    void aRemovalOfSomebodyWhoIsNotAMemberIs404() throws Exception {
        doThrow(new NotFoundException("Member not found.")).when(memberships).remove("spona", marko, MEMBER_ID);

        mvc.perform(delete(MEMBER_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Member not found."));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
