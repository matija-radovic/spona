package io.github.matijaradovic.spona.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.privacy.PrivacySettings;
import io.github.matijaradovic.spona.actor.privacy.PrivacySettingsRepository;
import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignment;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.support.AvatarFixtures;
import io.github.matijaradovic.spona.support.ContainersConfig;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
@Transactional
class OrganizationIntegrationTest {

    private static final String PROFILE_BODY = """
            {"displayName":"Nov Naziv","legalName":"Nov Naziv d.o.o.","description":null,
             "website":null,"avatar":null,"location":null,"otherContactInfo":[]}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private OrganizationMemberRepository members;

    @Autowired
    private RoleAssignmentRepository roles;

    @Autowired
    private PrivacySettingsRepository privacySettings;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aCreateWritesAllThreeRowsAndTheFounderReadsTheSettingsAsOwner() throws Exception {
        User founder = verified("orgfounder");

        mvc.perform(json(as(post("/organizations"), founder), """
                {"slug":"org-founded","displayName":"  Osnovana ","legalName":" Osnovana d.o.o. ",
                 "description":"   ","website":null,"avatar":null,"location":" Beograd ",
                 "otherContactInfo":[{"type":"email","description":"Posao","value":"posao@primer.rs"}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actorType").value("org"))
                .andExpect(jsonPath("$.slug").value("org-founded"))
                .andExpect(jsonPath("$.displayName").value("Osnovana"))
                .andExpect(jsonPath("$.legalName").value("Osnovana d.o.o."))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.location").value("Beograd"))
                .andExpect(jsonPath("$.createdAt").isString());
        entityManager.flush();
        entityManager.clear();

        Organization created = organizations.findBySlug("org-founded").orElseThrow();
        assertThat(members.countByOrganization_Id(created.getId())).isEqualTo(1);
        assertThat(roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(created.getId(), founder.getId()))
                .hasValueSatisfying(row -> {
                    assertThat(row.getRole()).isEqualTo(OrganizationRole.OWNER);
                    assertThat(row.getAssignedBy().getId()).isEqualTo(founder.getId());
                });

        mvc.perform(as(get("/organizations/org-founded/settings"), founder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.viewerRole").value("OWNER"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.privacy.showJobPostings").value(true))
                .andExpect(jsonPath("$.otherContactInfo.length()").value(1));
    }

    @Test
    void aCreateStoresTheAvatarItCarries() throws Exception {
        User founder = verified("orgavatarfounder");
        String avatar = AvatarFixtures.pngDataUrl(AvatarFixtures.canvasPng());

        mvc.perform(json(as(post("/organizations"), founder),
                "{\"slug\":\"org-with-avatar\",\"displayName\":\"Avatar\",\"legalName\":\"Avatar d.o.o.\","
                        + "\"avatar\":\"" + avatar + "\",\"otherContactInfo\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avatarUrl").value(endsWith(".png")));
    }

    @Test
    void aGuestMayNotCreateAnOrganization() throws Exception {
        mvc.perform(json(post("/organizations"), createBody("org-by-guest")))
                .andExpect(status().isUnauthorized());

        assertThat(organizations.existsBySlug("org-by-guest")).isFalse();
    }

    @Test
    void aReservedSlugIs409AndCreatesNothing() throws Exception {
        User founder = verified("orgreserved");

        mvc.perform(json(as(post("/organizations"), founder), createBody("companies")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/handle-reserved"));

        assertThat(organizations.existsBySlug("companies")).isFalse();
    }

    @Test
    void aTakenSlugIs409() throws Exception {
        User founder = verified("orgtaker");
        organizations.saveAndFlush(TestFixtures.organization("org-held"));

        mvc.perform(json(as(post("/organizations"), founder), createBody("org-held")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/slug-taken"));
    }

    @ParameterizedTest
    @EnumSource(OrganizationRole.class)
    void everyRoleReadsTheSettingsAndSeesItsOwnRole(OrganizationRole role) throws Exception {
        String name = role.name().toLowerCase(Locale.ROOT);
        Organization organization = organizations.save(TestFixtures.organization("org-read-" + name));
        User reader = member(organization, "orgread" + name, role);

        mvc.perform(as(get("/organizations/org-read-" + name + "/settings"), reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value(role.name()))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void aNonMemberIs403OnTheSettings() throws Exception {
        organizations.save(TestFixtures.organization("org-closed"));
        User outsider = verified("orgoutsider");

        mvc.perform(as(get("/organizations/org-closed/settings"), outsider))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRevokedRoleIs403OnTheSettings() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-revoked"));
        User former = member(organization, "orgformer", OrganizationRole.ADMIN);
        RoleAssignment granted = roles
                .findByOrganizationIdAndUserIdAndRevokedAtIsNull(organization.getId(), former.getId())
                .orElseThrow();
        granted.revoke(granted.getAssignedAt().plusSeconds(1));
        roles.saveAndFlush(granted);

        mvc.perform(as(get("/organizations/org-revoked/settings"), former))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownSlugIs404OnTheSettings() throws Exception {
        User reader = verified("orgnobodyreader");

        mvc.perform(as(get("/organizations/nepostojeci/settings"), reader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.instance").value("/organizations/nepostojeci/settings"));
    }

    @Test
    void aGuestIs401OnTheSettings() throws Exception {
        organizations.save(TestFixtures.organization("org-guest-read"));

        mvc.perform(get("/organizations/org-guest-read/settings")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationRole.class, names = { "PUBLISHER", "EDITOR", "RECRUITER" })
    void aRoleBelowAdminMayNotUpdateTheProfile(OrganizationRole role) throws Exception {
        String name = role.name().toLowerCase(Locale.ROOT);
        Organization organization = organizations.save(TestFixtures.organization("org-edit-" + name));
        User member = member(organization, "orgedit" + name, role);

        mvc.perform(json(as(put("/organizations/org-edit-" + name), member), PROFILE_BODY))
                .andExpect(status().isForbidden());
        entityManager.flush();
        entityManager.clear();

        assertThat(organizations.findById(organization.getId()).orElseThrow().getDisplayName())
                .isEqualTo("Display org-edit-" + name);
    }

    @Test
    void anAdminUpdatesTheProfileAndItIsStoredTrimmed() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-edited"));
        User admin = member(organization, "orgeditedadmin", OrganizationRole.ADMIN);

        mvc.perform(json(as(put("/organizations/org-edited"), admin), """
                {"displayName":" Nov Naziv ","legalName":"Nov Naziv d.o.o.","description":" O nama ",
                 "website":"  ","avatar":null,"location":null,"otherContactInfo":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nov Naziv"))
                .andExpect(jsonPath("$.website").value(nullValue()))
                .andExpect(jsonPath("$.viewerRole").value("ADMIN"))
                .andExpect(jsonPath("$.memberCount").value(1));
        entityManager.flush();
        entityManager.clear();

        Organization stored = organizations.findById(organization.getId()).orElseThrow();
        assertThat(stored.getDisplayName()).isEqualTo("Nov Naziv");
        assertThat(stored.getDescription()).isEqualTo("O nama");
        assertThat(stored.getWebsite()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationRole.class, names = "OWNER", mode = EnumSource.Mode.EXCLUDE)
    void onlyTheOwnerChangesTheSlugTransfersOrDeletes(OrganizationRole role) throws Exception {
        String name = role.name().toLowerCase(Locale.ROOT);
        String slug = "org-owned-" + name;
        Organization organization = organizations.save(TestFixtures.organization(slug));
        User member = member(organization, "orgowned" + name, role);

        mvc.perform(json(as(put("/organizations/" + slug + "/slug"), member), "{\"slug\":\"org-moved-" + name + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(as(post("/organizations/" + slug + "/transfer-ownership"), member),
                "{\"userId\":\"" + member.getId() + "\",\"confirmSlug\":\"" + slug + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(as(delete("/organizations/" + slug), member), "{\"confirmSlug\":\"" + slug + "\"}"))
                .andExpect(status().isForbidden());
        entityManager.flush();
        entityManager.clear();

        assertThat(organizations.findBySlug(slug)).isPresent();
        assertThat(roles.findLiveRole(organization.getId(), member.getId())).contains(role);
    }

    @Test
    void aSlugChangeMovesTheOrganizationToItsNewAddress() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-before"));
        User owner = member(organization, "orgmover", OrganizationRole.OWNER);

        mvc.perform(json(as(put("/organizations/org-before/slug"), owner), "{\"slug\":\"org-after\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("org-after"))
                .andExpect(jsonPath("$.viewerRole").value("OWNER"));
        entityManager.flush();

        mvc.perform(as(get("/organizations/org-after/settings"), owner)).andExpect(status().isOk());
        mvc.perform(get("/organizations/org-before")).andExpect(status().isNotFound());
    }

    @Test
    void aSlugChangeToAReservedSlugIs409() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-stays"));
        User owner = member(organization, "orgstayer", OrganizationRole.OWNER);

        mvc.perform(json(as(put("/organizations/org-stays/slug"), owner), "{\"slug\":\"settings\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/handle-reserved"));
    }

    @Test
    void aSlugChangeToATakenSlugIs409() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-stays-put"));
        organizations.save(TestFixtures.organization("org-occupied"));
        User owner = member(organization, "orgstaysput", OrganizationRole.OWNER);

        mvc.perform(json(as(put("/organizations/org-stays-put/slug"), owner), "{\"slug\":\"org-occupied\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/slug-taken"));
    }

    @Test
    void aTransferLeavesExactlyOneLiveOwnerAndThePreviousOwnerAnAdmin() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-handover"));
        User owner = member(organization, "orghandowner", OrganizationRole.OWNER);
        User admin = member(organization, "orghandadmin", OrganizationRole.ADMIN);
        entityManager.clear();

        mvc.perform(json(as(post("/organizations/org-handover/transfer-ownership"), owner),
                "{\"userId\":\"" + admin.getId() + "\",\"confirmSlug\":\"org-handover\"}"))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        UUID id = organization.getId();
        assertThat(roles.findLiveRole(id, admin.getId())).contains(OrganizationRole.OWNER);
        assertThat(roles.findLiveRole(id, owner.getId())).contains(OrganizationRole.ADMIN);
        assertThat(count("select count(*) from role_assignment where organization_id = :id"
                + " and role = 'OWNER' and revoked_at is null", id)).isEqualTo(1);
        assertThat(count("select count(*) from role_assignment where organization_id = :id", id)).isEqualTo(4);

        mvc.perform(as(get("/organizations/org-handover/settings"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("ADMIN"))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    void aTransferToAnEditorIs422AndChangesNothing() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-no-handover"));
        User owner = member(organization, "orgkeeper", OrganizationRole.OWNER);
        User editor = member(organization, "orgkeepereditor", OrganizationRole.EDITOR);

        mvc.perform(json(as(post("/organizations/org-no-handover/transfer-ownership"), owner),
                "{\"userId\":\"" + editor.getId() + "\",\"confirmSlug\":\"org-no-handover\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.userId").exists());

        assertThat(roles.findLiveRole(organization.getId(), owner.getId())).contains(OrganizationRole.OWNER);
        assertThat(roles.findLiveRole(organization.getId(), editor.getId())).contains(OrganizationRole.EDITOR);
    }

    @Test
    void aTransferWithAMismatchedConfirmationIs422() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-unconfirmed"));
        User owner = member(organization, "orgunconfirmed", OrganizationRole.OWNER);
        User admin = member(organization, "orgunconfirmedadmin", OrganizationRole.ADMIN);

        mvc.perform(json(as(post("/organizations/org-unconfirmed/transfer-ownership"), owner),
                "{\"userId\":\"" + admin.getId() + "\",\"confirmSlug\":\"org-unconfirme\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.confirmSlug").exists());

        assertThat(roles.findLiveRole(organization.getId(), owner.getId())).contains(OrganizationRole.OWNER);
    }

    @Test
    void aDeleteRemovesTheOrganizationItsMembershipsRolesAndPrivacyButNotItsMembers() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-doomed"));
        User owner = member(organization, "orgdoomedowner", OrganizationRole.OWNER);
        User editor = member(organization, "orgdoomededitor", OrganizationRole.EDITOR);
        privacySettings.saveAndFlush(new PrivacySettings(organization.getId()));
        entityManager.clear();

        mvc.perform(json(as(delete("/organizations/org-doomed"), owner), "{\"confirmSlug\":\"org-doomed\"}"))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        UUID id = organization.getId();
        assertThat(count("select count(*) from actor where id = :id", id)).isZero();
        assertThat(count("select count(*) from organization where id = :id", id)).isZero();
        assertThat(count("select count(*) from organization_member where organization_id = :id", id)).isZero();
        assertThat(count("select count(*) from role_assignment where organization_id = :id", id)).isZero();
        assertThat(privacySettings.findById(id)).isEmpty();
        assertThat(users.findById(owner.getId())).isPresent();
        assertThat(users.findById(editor.getId())).isPresent();
    }

    @Test
    void aDeleteWithAMismatchedConfirmationIs422AndDeletesNothing() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("org-spared"));
        User owner = member(organization, "orgspared", OrganizationRole.OWNER);

        mvc.perform(json(as(delete("/organizations/org-spared"), owner), "{\"confirmSlug\":\"org-spare\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.confirmSlug").exists());

        assertThat(organizations.findBySlug("org-spared")).isPresent();
    }

    private User verified(String username) {
        User user = TestFixtures.user(username);
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private User member(Organization organization, String username, OrganizationRole role) {
        User user = verified(username);
        members.saveAndFlush(TestFixtures.membership(user, organization));
        roles.saveAndFlush(TestFixtures.roleAssignment(user, organization, role));
        return user;
    }

    private long count(String sql, UUID id) {
        return ((Number) entityManager.createNativeQuery(sql).setParameter("id", id).getSingleResult()).longValue();
    }

    private static String createBody(String slug) {
        return "{\"slug\":\"" + slug + "\",\"displayName\":\"Firma\",\"legalName\":\"Firma d.o.o.\","
                + "\"otherContactInfo\":[]}";
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(user));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
