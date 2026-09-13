package io.github.matijaradovic.spona.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.matijaradovic.spona.actor.ActorService;
import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.organization.dto.CreateOrganizationRequest;
import io.github.matijaradovic.spona.organization.dto.OrgSettingsDto;
import io.github.matijaradovic.spona.organization.dto.OrganizationDto;
import io.github.matijaradovic.spona.organization.dto.TransferOwnershipRequest;
import io.github.matijaradovic.spona.organization.member.MembershipService;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class OrganizationSettingsControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000d4");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e5");
    private static final String CREATE_BODY = """
            {"slug":"spona","displayName":"Spona","legalName":"Spona d.o.o.","description":null,
             "website":null,"avatar":null,"location":null,"otherContactInfo":[]}""";
    private static final String PROFILE_BODY = """
            {"displayName":"Spona","legalName":"Spona d.o.o.","description":null,
             "website":null,"avatar":null,"location":null,"otherContactInfo":[]}""";

    private final ActorService actors = mock(ActorService.class);
    private final OrganizationService service = mock(OrganizationService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new OrganizationController(actors, service, mock(MembershipService.class)))
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
    void aCreateIs201AndAnswersTheOrganizationWithItsLegalName() throws Exception {
        when(service.create(eq(marko), any())).thenReturn(created());

        mvc.perform(json(post("/organizations"), CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ORG_ID.toString()))
                .andExpect(jsonPath("$.actorType").value("org"))
                .andExpect(jsonPath("$.slug").value("spona"))
                .andExpect(jsonPath("$.legalName").value("Legal spona d.o.o."))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.followers").doesNotExist())
                .andExpect(jsonPath("$.viewerRole").doesNotExist());
    }

    @Test
    void aCreateHandsTheCallerAndTheBodyToTheService() throws Exception {
        when(service.create(eq(marko), any())).thenReturn(created());

        mvc.perform(json(post("/organizations"), CREATE_BODY)).andExpect(status().isCreated());

        verify(service).create(marko, new CreateOrganizationRequest("spona", "Spona", "Spona d.o.o.",
                null, null, null, null, List.of()));
    }

    @Test
    void aSlugThatIsNotOneLowercaseSegmentIs422UnderSlug() throws Exception {
        mvc.perform(json(post("/organizations"), """
                {"slug":"Nova Firma","displayName":"Nova","legalName":"Nova d.o.o.","otherContactInfo":[]}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.slug").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aSlugOverFortyCharactersIs422UnderSlug() throws Exception {
        mvc.perform(json(post("/organizations"), "{\"slug\":\"" + "a".repeat(41)
                + "\",\"displayName\":\"Nova\",\"legalName\":\"Nova d.o.o.\",\"otherContactInfo\":[]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.slug").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aBlankDisplayNameIs422UnderDisplayName() throws Exception {
        mvc.perform(json(post("/organizations"), """
                {"slug":"nova","displayName":"   ","legalName":"Nova d.o.o.","otherContactInfo":[]}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.displayName").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aLegalNameOverTheBoundIs422UnderLegalName() throws Exception {
        mvc.perform(json(post("/organizations"), "{\"slug\":\"nova\",\"displayName\":\"Nova\",\"legalName\":\""
                + "a".repeat(161) + "\",\"otherContactInfo\":[]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.legalName").exists());

        verifyNoInteractions(service);
    }

    @Test
    void sixteenContactEntriesAre422() throws Exception {
        String entries = IntStream.range(0, 16)
                .mapToObj(i -> "{\"type\":\"email\",\"description\":\"\",\"value\":\"k" + i + "@primer.rs\"}")
                .collect(Collectors.joining(","));

        mvc.perform(json(post("/organizations"),
                "{\"slug\":\"nova\",\"displayName\":\"Nova\",\"legalName\":\"Nova d.o.o.\",\"otherContactInfo\":["
                        + entries + "]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.otherContactInfo").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aReservedSlugCarriesItsProblemType() throws Exception {
        when(service.create(eq(marko), any()))
                .thenThrow(new ConflictException("That slug is reserved.", ProblemTypes.HANDLE_RESERVED));

        mvc.perform(json(post("/organizations"), CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/handle-reserved"));
    }

    @Test
    void aTakenSlugCarriesItsProblemType() throws Exception {
        when(service.create(eq(marko), any()))
                .thenThrow(new ConflictException("That slug is taken.", ProblemTypes.SLUG_TAKEN));

        mvc.perform(json(post("/organizations"), CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/slug-taken"));
    }

    @Test
    void servesTheSettingsWithTheViewerRoleTheMemberCountAndPrivacy() throws Exception {
        when(service.settings("spona", marko)).thenReturn(settings("spona", OrganizationRole.EDITOR));

        mvc.perform(get("/organizations/spona/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORG_ID.toString()))
                .andExpect(jsonPath("$.legalName").value("Legal spona d.o.o."))
                .andExpect(jsonPath("$.otherContactInfo.length()").value(2))
                .andExpect(jsonPath("$.privacy.showJobPostings").value(false))
                .andExpect(jsonPath("$.viewerRole").value("EDITOR"))
                .andExpect(jsonPath("$.memberCount").value(7))
                .andExpect(jsonPath("$.actorType").doesNotExist())
                .andExpect(jsonPath("$.followers").doesNotExist());
    }

    @Test
    void theSettingsOfASlugThatIsNobodyAre404() throws Exception {
        when(service.settings("nepostojeci", marko)).thenThrow(new NotFoundException("Organization not found."));

        mvc.perform(get("/organizations/nepostojeci/settings"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Organization not found."));
    }

    @Test
    void theSettingsOfAnOrganizationTheCallerIsNotInAre403() throws Exception {
        when(service.settings("spona", marko))
                .thenThrow(new ForbiddenException("You are not a member of this organization."));

        mvc.perform(get("/organizations/spona/settings")).andExpect(status().isForbidden());
    }

    @Test
    void aProfileUpdateAnswersTheWholeSettings() throws Exception {
        when(service.updateProfile(eq("spona"), eq(marko), any()))
                .thenReturn(settings("spona", OrganizationRole.ADMIN));

        mvc.perform(json(put("/organizations/spona"), PROFILE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Display spona"))
                .andExpect(jsonPath("$.viewerRole").value("ADMIN"))
                .andExpect(jsonPath("$.privacy.showJobPostings").value(false));
    }

    @Test
    void aProfileUpdateByAMemberWhoMayNotEditIs403() throws Exception {
        when(service.updateProfile(eq("spona"), eq(marko), any()))
                .thenThrow(new ForbiddenException("Only the owner or an admin may change this organization."));

        mvc.perform(json(put("/organizations/spona"), PROFILE_BODY)).andExpect(status().isForbidden());
    }

    @Test
    void aDescriptionOverTheBoundIs422UnderDescription() throws Exception {
        mvc.perform(json(put("/organizations/spona"), "{\"displayName\":\"Spona\",\"legalName\":\"Spona d.o.o.\","
                + "\"description\":\"" + "a".repeat(1001) + "\",\"otherContactInfo\":[]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.description").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aWebsiteOverTheBoundIs422UnderWebsite() throws Exception {
        mvc.perform(json(put("/organizations/spona"), "{\"displayName\":\"Spona\",\"legalName\":\"Spona d.o.o.\","
                + "\"website\":\"https://" + "a".repeat(193) + "\",\"otherContactInfo\":[]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.website").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aSlugChangeAnswersTheNewSlug() throws Exception {
        when(service.changeSlug("spona", marko, "spona-nova")).thenReturn(settings("spona-nova", OrganizationRole.OWNER));

        mvc.perform(json(put("/organizations/spona/slug"), "{\"slug\":\"spona-nova\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("spona-nova"));
    }

    @Test
    void aSlugChangeToATakenSlugCarriesItsProblemType() throws Exception {
        when(service.changeSlug("spona", marko, "zauzeto"))
                .thenThrow(new ConflictException("That slug is taken.", ProblemTypes.SLUG_TAKEN));

        mvc.perform(json(put("/organizations/spona/slug"), "{\"slug\":\"zauzeto\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/slug-taken"));
    }

    @Test
    void aMalformedNextSlugIs422UnderSlug() throws Exception {
        mvc.perform(json(put("/organizations/spona/slug"), "{\"slug\":\"ab\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.slug").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aSlugChangeByAnAdminIs403() throws Exception {
        when(service.changeSlug("spona", marko, "spona-nova"))
                .thenThrow(new ForbiddenException("Only the owner may do this."));

        mvc.perform(json(put("/organizations/spona/slug"), "{\"slug\":\"spona-nova\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTransferIs204AndHandsTheBodyToTheService() throws Exception {
        mvc.perform(json(post("/organizations/spona/transfer-ownership"),
                "{\"userId\":\"" + ADMIN_ID + "\",\"confirmSlug\":\"spona\"}"))
                .andExpect(status().isNoContent());

        verify(service).transferOwnership("spona", marko, new TransferOwnershipRequest(ADMIN_ID, "spona"));
    }

    @Test
    void aTransferWithoutANewOwnerIs422UnderUserId() throws Exception {
        mvc.perform(json(post("/organizations/spona/transfer-ownership"), "{\"confirmSlug\":\"spona\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.userId").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aTransferWithAMismatchedConfirmationIs422WithTheDetail() throws Exception {
        doThrow(UnprocessableException.on("confirmSlug", "The slug you typed does not match."))
                .when(service).transferOwnership(eq("spona"), eq(marko), any());

        mvc.perform(json(post("/organizations/spona/transfer-ownership"),
                "{\"userId\":\"" + ADMIN_ID + "\",\"confirmSlug\":\"spon\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("The slug you typed does not match."));
    }

    @Test
    void aDeleteIs204AndReadsTheConfirmationFromTheBody() throws Exception {
        mvc.perform(json(delete("/organizations/spona"), "{\"confirmSlug\":\"spona\"}"))
                .andExpect(status().isNoContent());

        verify(service).delete("spona", marko, "spona");
    }

    @Test
    void aDeleteWithABlankConfirmationIs422() throws Exception {
        mvc.perform(json(delete("/organizations/spona"), "{\"confirmSlug\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.confirmSlug").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aDeleteWithNoBodyIsRefused() throws Exception {
        mvc.perform(delete("/organizations/spona")).andExpect(status().is4xxClientError());

        verifyNoInteractions(service);
    }

    @Test
    void aDeleteByAnAdminIs403() throws Exception {
        doThrow(new ForbiddenException("Only the owner may do this."))
                .when(service).delete("spona", marko, "spona");

        mvc.perform(json(delete("/organizations/spona"), "{\"confirmSlug\":\"spona\"}"))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static OrganizationDto created() {
        Organization organization = TestFixtures.organization("spona");
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
        ReflectionTestUtils.setField(organization, "createdAt", Instant.parse("2026-09-13T10:00:00Z"));
        return OrganizationDto.from(organization);
    }

    private static OrgSettingsDto settings(String slug, OrganizationRole role) {
        Organization organization = TestFixtures.organization(slug);
        ReflectionTestUtils.setField(organization, "id", ORG_ID);
        organization.setOtherContactInfo(TestFixtures.contacts());
        return OrgSettingsDto.from(organization, new PrivacyDto(false), role, 7);
    }
}
