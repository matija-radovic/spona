package io.github.matijaradovic.spona.organization;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import io.github.matijaradovic.spona.organization.dto.InvitationDto;
import io.github.matijaradovic.spona.organization.invitation.InvitationService;
import io.github.matijaradovic.spona.organization.invitation.InvitationStatus;
import io.github.matijaradovic.spona.organization.member.MembershipService;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class OrganizationInvitationsControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000003c1");
    private static final UUID INVITATION_ID = UUID.fromString("00000000-0000-4000-8000-0000000003c2");
    private static final UUID DECLINED_ID = UUID.fromString("00000000-0000-4000-8000-0000000003c3");
    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05Z");
    private static final Instant RESOLVED = Instant.parse("2026-01-05T06:07:08Z");
    private static final String INVITATIONS_PATH = "/organizations/spona/invitations";
    private static final String INVITATION_PATH = INVITATIONS_PATH + "/" + INVITATION_ID;

    private final InvitationService invitations = mock(InvitationService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new OrganizationController(mock(ActorService.class), mock(OrganizationService.class),
                    mock(MembershipService.class), invitations))
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
    void theListIsABareArrayWhereEachRowCarriesOneArmAndBothKeys() throws Exception {
        when(invitations.list("spona", marko)).thenReturn(List.of(
                new InvitationDto(INVITATION_ID, InvitationStatus.WAITING,
                        new InvitationDto.Invitee("jelena", "Jelena Jović", null), null, OrganizationRole.EDITOR,
                        CREATED, null, "marko"),
                new InvitationDto(DECLINED_ID, InvitationStatus.DECLINED, null, "nova@primer.rs",
                        OrganizationRole.RECRUITER, CREATED, RESOLVED, null)));

        mvc.perform(get(INVITATIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(INVITATION_ID.toString()))
                .andExpect(jsonPath("$[0].status").value("WAITING"))
                .andExpect(jsonPath("$[0].invitee.username").value("jelena"))
                .andExpect(jsonPath("$[0].invitee.fullName").value("Jelena Jović"))
                .andExpect(jsonPath("$[0].invitee.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$[0].email").value(nullValue()))
                .andExpect(jsonPath("$[0].role").value("EDITOR"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-01-02T03:04:05Z"))
                .andExpect(jsonPath("$[0].resolvedAt").value(nullValue()))
                .andExpect(jsonPath("$[0].invitedByUsername").value("marko"))
                .andExpect(jsonPath("$[1].status").value("DECLINED"))
                .andExpect(jsonPath("$[1].invitee").value(nullValue()))
                .andExpect(jsonPath("$[1].email").value("nova@primer.rs"))
                .andExpect(jsonPath("$[1].resolvedAt").value("2026-01-05T06:07:08Z"))
                .andExpect(jsonPath("$[1].invitedByUsername").value(nullValue()))
                .andExpect(jsonPath("$[1].dismissedAt").doesNotExist());
    }

    @Test
    void theListForARoleBelowAdminIs403() throws Exception {
        when(invitations.list("spona", marko))
                .thenThrow(new ForbiddenException("Only the owner or an admin may manage invitations."));

        mvc.perform(get(INVITATIONS_PATH)).andExpect(status().isForbidden());
    }

    @Test
    void anInvitationIs201WithTheRowAndHandsTheIdentifierAndTheRoleToTheService() throws Exception {
        when(invitations.create("spona", marko, "nova@primer.rs", OrganizationRole.RECRUITER)).thenReturn(
                new InvitationDto(INVITATION_ID, InvitationStatus.WAITING, null, "nova@primer.rs",
                        OrganizationRole.RECRUITER, CREATED, null, "marko"));

        mvc.perform(invite("nova@primer.rs", "RECRUITER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(INVITATION_ID.toString()))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.invitee").value(nullValue()))
                .andExpect(jsonPath("$.email").value("nova@primer.rs"))
                .andExpect(jsonPath("$.role").value("RECRUITER"));
    }

    @Test
    void theIdentifierIsBoundedAt254AndByNothingNarrower() throws Exception {
        mvc.perform(invite("a".repeat(40), "EDITOR")).andExpect(status().isCreated());
        mvc.perform(invite("a".repeat(242) + "@example.com", "EDITOR")).andExpect(status().isCreated());
        mvc.perform(invite("a".repeat(243) + "@example.com", "EDITOR"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.identifier").value("At most 254 characters."));
    }

    @Test
    void aBlankIdentifierIs422UnderIdentifierAndTheServiceIsNotCalled() throws Exception {
        mvc.perform(invite("   ", "EDITOR"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.identifier").exists());

        verifyNoInteractions(invitations);
    }

    @Test
    void anInvitationWithoutARoleIs422UnderRole() throws Exception {
        mvc.perform(json(post(INVITATIONS_PATH), "{\"identifier\":\"jelena\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.role").exists());

        verifyNoInteractions(invitations);
    }

    @Test
    void aUsernameNobodyHoldsIs422UnderIdentifier() throws Exception {
        when(invitations.create("spona", marko, "nepostojeci", OrganizationRole.EDITOR))
                .thenThrow(UnprocessableException.on("identifier", "No user has this username."));

        mvc.perform(invite("nepostojeci", "EDITOR"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.identifier").value("No user has this username."));
    }

    @Test
    void aUsernameAlreadyInTheOrganizationIs409AlreadyMember() throws Exception {
        when(invitations.create("spona", marko, "jelena", OrganizationRole.EDITOR))
                .thenThrow(new ConflictException("This user is already a member.", ProblemTypes.ALREADY_MEMBER));

        mvc.perform(invite("jelena", "EDITOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/already-member"));
    }

    @Test
    void cancelIs204AndHandsTheSlugTheCallerAndTheIdToTheService() throws Exception {
        mvc.perform(delete(INVITATION_PATH)).andExpect(status().isNoContent());

        verify(invitations).cancel("spona", marko, INVITATION_ID);
    }

    @Test
    void cancelOnARowThatIsNoLongerWaitingIs409() throws Exception {
        doThrow(new ConflictException("Only a waiting invitation can be canceled."))
                .when(invitations).cancel("spona", marko, INVITATION_ID);

        mvc.perform(delete(INVITATION_PATH)).andExpect(status().isConflict());
    }

    @Test
    void dismissIs204AndHandsTheSlugTheCallerAndTheIdToTheService() throws Exception {
        mvc.perform(post(INVITATION_PATH + "/dismiss")).andExpect(status().isNoContent());

        verify(invitations).dismiss("spona", marko, INVITATION_ID);
    }

    @Test
    void anIdThatIsNotThisOrganizationsIs404() throws Exception {
        doThrow(new NotFoundException("Invitation not found."))
                .when(invitations).dismiss("spona", marko, INVITATION_ID);

        mvc.perform(post(INVITATION_PATH + "/dismiss"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Invitation not found."));
    }

    private static MockHttpServletRequestBuilder invite(String identifier, String role) {
        return json(post(INVITATIONS_PATH), "{\"identifier\":\"" + identifier + "\",\"role\":\"" + role + "\"}");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
