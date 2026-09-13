package io.github.matijaradovic.spona.organization.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.common.util.SecureTokens;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.support.ContainersConfig;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
@Transactional
class InvitationIntegrationTest {

    private static final String PASSWORD = "Lozinka123";

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
    private OrganizationInvitationRepository invitations;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aGuestIs401OnEveryInvitationRoute() throws Exception {
        String id = UUID.randomUUID().toString();

        mvc.perform(get("/organizations/org-any/invitations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/organizations/org-any/invitations")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/organizations/org-any/invitations/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/organizations/org-any/invitations/" + id + "/dismiss")).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/anyone/invitations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/invitations/" + id + "/accept")).andExpect(status().isUnauthorized());
        mvc.perform(post("/invitations/" + id + "/decline")).andExpect(status().isUnauthorized());
    }

    
    @Test
    void anAddressInvitationServesTheAddressAndNeverTheAccountThatHoldsIt() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-arms-b"));
        User owner = member(organization, "addrowner", OrganizationRole.OWNER);
        verified("addrholder");

        mvc.perform(invite(owner, "inv-arms-b", "  AddrHolder@Example.com ", "RECRUITER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitee").value(nullValue()))
                .andExpect(jsonPath("$.email").value("addrholder@example.com"));

        mvc.perform(as(get("/organizations/inv-arms-b/invitations"), owner))
                .andExpect(jsonPath("$[0].invitee").value(nullValue()))
                .andExpect(jsonPath("$[0].email").value("addrholder@example.com"));
        assertThat(entityManager.createNativeQuery("select count(*) from organization_invitations"
                + " where organization_id = :id and invitee_user_id is null")
                .setParameter("id", organization.getId()).getSingleResult()).isEqualTo(1L);
    }

    @Test
    void aUsernameInvitationServesTheInviteeAndNeverTheAddressItSnapshots() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-arms-a"));
        User owner = member(organization, "armsowner", OrganizationRole.OWNER);
        verified("armsinvitee");

        mvc.perform(invite(owner, "inv-arms-a", "armsinvitee", "EDITOR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.invitee.username").value("armsinvitee"))
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.invitedByUsername").value("armsowner"));

        mvc.perform(as(get("/organizations/inv-arms-a/invitations"), owner))
                .andExpect(jsonPath("$[0].invitee.username").value("armsinvitee"))
                .andExpect(jsonPath("$[0].email").value(nullValue()));
        assertThat(entityManager.createNativeQuery("select invitee_email from organization_invitations"
                + " where organization_id = :id").setParameter("id", organization.getId()).getSingleResult())
                .isEqualTo("armsinvitee@example.com");
    }

    @Test
    void theOrganizationListsWaitingAndUndismissedDeclinedRowsNewestFirst() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-list"));
        User owner = member(organization, "listowner", OrganizationRole.OWNER);
        User invitee = verified("listinvitee");
        OrganizationInvitation older = stored(TestFixtures.invitation(organization, invitee, owner,
                OrganizationRole.EDITOR));
        OrganizationInvitation newer = stored(TestFixtures.addressInvitation(organization, "novi@primer.rs", owner,
                OrganizationRole.RECRUITER));
        OrganizationInvitation declined = TestFixtures.addressInvitation(organization, "odbio@primer.rs", owner,
                OrganizationRole.EDITOR);
        declined.decline(Instant.now());
        stored(declined);
        OrganizationInvitation dismissed = TestFixtures.addressInvitation(organization, "sklonjen@primer.rs", owner,
                OrganizationRole.EDITOR);
        dismissed.decline(Instant.now());
        dismissed.dismiss(Instant.now());
        stored(dismissed);
        OrganizationInvitation canceled = TestFixtures.addressInvitation(organization, "otkazan@primer.rs", owner,
                OrganizationRole.EDITOR);
        canceled.cancel(Instant.now());
        stored(canceled);
        OrganizationInvitation accepted = TestFixtures.invitation(organization, invitee, owner,
                OrganizationRole.EDITOR);
        accepted.accept(invitee, Instant.now());
        stored(accepted);
        created(older, 5);
        created(newer, 1);
        created(declined, 3);
        created(dismissed, 2);
        created(canceled, 4);
        created(accepted, 6);
        entityManager.clear();

        mvc.perform(as(get("/organizations/inv-list/invitations"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(declined.getId().toString()))
                .andExpect(jsonPath("$[1].status").value("DECLINED"))
                .andExpect(jsonPath("$[1].resolvedAt").value(notNullValue()))
                .andExpect(jsonPath("$[2].id").value(older.getId().toString()))
                .andExpect(jsonPath("$[2].resolvedAt").value(nullValue()));
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationRole.class, names = { "PUBLISHER", "EDITOR", "RECRUITER" })
    void aRoleBelowAdminIsRefusedEveryOrganizationRoute(OrganizationRole role) throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-below"));
        User owner = member(organization, "belowowner", OrganizationRole.OWNER);
        User caller = member(organization, "belowcaller", role);
        OrganizationInvitation invitation = stored(TestFixtures.addressInvitation(organization, "neko@primer.rs",
                owner, OrganizationRole.EDITOR));
        entityManager.clear();
        String path = "/organizations/inv-below/invitations/" + invitation.getId();

        mvc.perform(as(get("/organizations/inv-below/invitations"), caller)).andExpect(status().isForbidden());
        mvc.perform(invite(caller, "inv-below", "novi@primer.rs", "EDITOR")).andExpect(status().isForbidden());
        mvc.perform(as(delete(path), caller)).andExpect(status().isForbidden());
        mvc.perform(as(post(path + "/dismiss"), caller)).andExpect(status().isForbidden());

        assertThat(count(organization)).isEqualTo(1);
        assertThat(reread(invitation.getId()).getStatus()).isEqualTo(InvitationStatus.WAITING);
    }

    @Test
    void anAdminInvitesBelowAdminButNotAtAdmin() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-admin"));
        member(organization, "adminowner", OrganizationRole.OWNER);
        User admin = member(organization, "adminadmin", OrganizationRole.ADMIN);

        mvc.perform(invite(admin, "inv-admin", "drugi.admin@primer.rs", "ADMIN")).andExpect(status().isForbidden());
        mvc.perform(invite(admin, "inv-admin", "urednik@primer.rs", "PUBLISHER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PUBLISHER"))
                .andExpect(jsonPath("$.invitedByUsername").value("adminadmin"));

        assertThat(count(organization)).isEqualTo(1);
    }

    @Test
    void ownershipIsNeverOfferedAndIs422UnderRole() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-owner"));
        User owner = member(organization, "ownerowner", OrganizationRole.OWNER);

        mvc.perform(invite(owner, "inv-owner", "vlasnik@primer.rs", "OWNER"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.role").exists());

        assertThat(count(organization)).isZero();
    }

    @Test
    void aUsernameIsCheckedAndAnAddressIsNot() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-classify"));
        User owner = member(organization, "classifyowner", OrganizationRole.OWNER);
        member(organization, "classifymember", OrganizationRole.EDITOR);

        mvc.perform(invite(owner, "inv-classify", "nepostojeci", "EDITOR"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.identifier").exists());
        mvc.perform(invite(owner, "inv-classify", "classifymember", "EDITOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/already-member"));
        assertThat(count(organization)).isZero();

        mvc.perform(invite(owner, "inv-classify", "classifymember@example.com", "EDITOR"))
                .andExpect(status().isCreated());
        mvc.perform(invite(owner, "inv-classify", "nepostojeci@primer.rs", "EDITOR"))
                .andExpect(status().isCreated());
    }

    @Test
    void aSecondWaitingInvitationForTheSameUsernameIs409() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-twice"));
        User owner = member(organization, "twiceowner", OrganizationRole.OWNER);
        verified("twiceinvitee");

        mvc.perform(invite(owner, "inv-twice", "twiceinvitee", "EDITOR")).andExpect(status().isCreated());
        mvc.perform(invite(owner, "inv-twice", "twiceinvitee", "RECRUITER")).andExpect(status().isConflict());

        assertThat(count(organization)).isEqualTo(1);
    }

    @Test
    void myInvitationsAreTheUnionOfBothArmsNewestFirst() throws Exception {
        Organization first = organizations.save(TestFixtures.organization("inv-union-a"));
        Organization second = organizations.save(TestFixtures.organization("inv-union-b"));
        User owner = member(first, "unionowner", OrganizationRole.OWNER);
        join(owner, second, OrganizationRole.OWNER);
        User me = verified("unionme");
        User other = verified("unionother");
        OrganizationInvitation byUsername = stored(TestFixtures.invitation(first, me, owner, OrganizationRole.EDITOR));
        OrganizationInvitation byAddress = stored(TestFixtures.addressInvitation(second, "unionme@example.com", owner,
                OrganizationRole.RECRUITER));
        stored(TestFixtures.addressInvitation(first, "neko@primer.rs", owner, OrganizationRole.EDITOR));
        stored(TestFixtures.invitation(second, other, owner, OrganizationRole.EDITOR));
        OrganizationInvitation declined = TestFixtures.invitation(second, me, owner, OrganizationRole.EDITOR);
        declined.decline(Instant.now());
        stored(declined);
        created(byUsername, 3);
        created(byAddress, 1);
        entityManager.clear();

        mvc.perform(as(get("/users/unionme/invitations"), me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(byAddress.getId().toString()))
                .andExpect(jsonPath("$[0].organization.actorType").value("org"))
                .andExpect(jsonPath("$[0].organization.handle").value("inv-union-b"))
                .andExpect(jsonPath("$[0].role").value("RECRUITER"))
                .andExpect(jsonPath("$[0].invitedBy.actorType").value("user"))
                .andExpect(jsonPath("$[0].invitedBy.handle").value("unionowner"))
                .andExpect(jsonPath("$[1].id").value(byUsername.getId().toString()))
                .andExpect(jsonPath("$[1].organization.handle").value("inv-union-a"));
    }

    @Test
    void anotherAccountsInvitationsAreNotYoursToRead() throws Exception {
        User me = verified("nosyinvitee");
        verified("nosyinvitetarget");

        mvc.perform(as(get("/users/nosyinvitetarget/invitations"), me)).andExpect(status().isForbidden());
    }

    @Test
    void acceptingJoinsAtTheOfferedRoleAndMyOrganizationsNameTheInviter() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-accept"));
        User owner = member(organization, "acceptowner", OrganizationRole.OWNER);
        User me = verified("acceptme");
        OrganizationInvitation invitation = stored(TestFixtures.addressInvitation(organization, "acceptme@example.com",
                owner, OrganizationRole.RECRUITER));
        entityManager.clear();

        mvc.perform(as(post("/invitations/" + invitation.getId() + "/accept"), me)).andExpect(status().isNoContent());

        OrganizationInvitation accepted = reread(invitation.getId());
        assertThat(accepted.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(accepted.getResolvedAt()).isNotNull();
        assertThat(accepted.getInvitee().getId()).isEqualTo(me.getId());
        assertThat(roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(organization.getId(), me.getId())
                .orElseThrow().getAssignedBy().getId()).isEqualTo(owner.getId());

        mvc.perform(as(get("/users/acceptme/organizations"), me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].organization.handle").value("inv-accept"))
                .andExpect(jsonPath("$[0].role").value("RECRUITER"))
                .andExpect(jsonPath("$[0].memberCount").value(2))
                .andExpect(jsonPath("$[0].invitedBy.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$[0].invitedBy.handle").value("acceptowner"));
        mvc.perform(as(get("/users/acceptme/invitations"), me)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(get("/organizations/inv-accept/members"), owner))
                .andExpect(jsonPath("$[1].username").value("acceptme"))
                .andExpect(jsonPath("$[1].role").value("RECRUITER"));
    }

    @Test
    void someoneElsesInvitationIs403NotYoursAndAnIdThatIsNobodyIs404() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-notyours"));
        User owner = member(organization, "notyoursowner", OrganizationRole.OWNER);
        User me = verified("notyoursme");
        User other = verified("notyoursother");
        OrganizationInvitation bound = stored(TestFixtures.invitation(organization, other, owner,
                OrganizationRole.EDITOR));
        OrganizationInvitation unbound = stored(TestFixtures.addressInvitation(organization, "neko.drugi@primer.rs",
                owner, OrganizationRole.EDITOR));
        entityManager.clear();

        mvc.perform(as(post("/invitations/" + bound.getId() + "/accept"), me))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/invitation-not-yours"));
        mvc.perform(as(post("/invitations/" + unbound.getId() + "/decline"), me))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/invitation-not-yours"));
        mvc.perform(as(post("/invitations/" + UUID.randomUUID() + "/accept"), me)).andExpect(status().isNotFound());

        assertThat(reread(bound.getId()).getStatus()).isEqualTo(InvitationStatus.WAITING);
        assertThat(reread(unbound.getId()).getStatus()).isEqualTo(InvitationStatus.WAITING);
    }

    @Test
    void aDeclinedRowStaysOnTheOrganizationsListUntilItIsDismissed() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-decline"));
        User owner = member(organization, "declineowner", OrganizationRole.OWNER);
        User me = verified("declineme");
        OrganizationInvitation invitation = stored(TestFixtures.invitation(organization, me, owner,
                OrganizationRole.EDITOR));
        entityManager.clear();

        mvc.perform(as(post("/invitations/" + invitation.getId() + "/decline"), me)).andExpect(status().isNoContent());
        mvc.perform(as(get("/users/declineme/invitations"), me)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(get("/organizations/inv-decline/invitations"), owner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("DECLINED"))
                .andExpect(jsonPath("$[0].resolvedAt").value(notNullValue()));

        mvc.perform(as(post("/organizations/inv-decline/invitations/" + invitation.getId() + "/dismiss"), owner))
                .andExpect(status().isNoContent());
        mvc.perform(as(get("/organizations/inv-decline/invitations"), owner)).andExpect(jsonPath("$.length()").value(0));

        OrganizationInvitation dismissed = reread(invitation.getId());
        assertThat(dismissed.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(dismissed.getDismissedAt()).isNotNull();
    }

    @Test
    void aCanceledRowLeavesBothListsAndEveryWrongStatusIs409() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-cancel"));
        User owner = member(organization, "cancelowner", OrganizationRole.OWNER);
        User me = verified("cancelme");
        OrganizationInvitation canceled = stored(TestFixtures.invitation(organization, me, owner,
                OrganizationRole.EDITOR));
        OrganizationInvitation waiting = stored(TestFixtures.addressInvitation(organization, "ceka@primer.rs", owner,
                OrganizationRole.EDITOR));
        entityManager.clear();
        String path = "/organizations/inv-cancel/invitations/";

        mvc.perform(as(delete(path + canceled.getId()), owner)).andExpect(status().isNoContent());
        mvc.perform(as(get("/organizations/inv-cancel/invitations"), owner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(waiting.getId().toString()));
        mvc.perform(as(get("/users/cancelme/invitations"), me)).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(as(delete(path + canceled.getId()), owner)).andExpect(status().isConflict());
        mvc.perform(as(post(path + waiting.getId() + "/dismiss"), owner)).andExpect(status().isConflict());
        mvc.perform(as(post("/invitations/" + canceled.getId() + "/accept"), me)).andExpect(status().isConflict());

        OrganizationInvitation row = reread(canceled.getId());
        assertThat(row.getStatus()).isEqualTo(InvitationStatus.CANCELED);
        assertThat(row.getResolvedAt()).isNotNull();
    }

    @Test
    void anIdFromAnotherOrganizationIs404OnCancelAndDismiss() throws Exception {
        Organization mine = organizations.save(TestFixtures.organization("inv-mine"));
        Organization theirs = organizations.save(TestFixtures.organization("inv-theirs"));
        User owner = member(mine, "inmineowner", OrganizationRole.OWNER);
        User theirOwner = member(theirs, "intheirsowner", OrganizationRole.OWNER);
        OrganizationInvitation theirRow = stored(TestFixtures.addressInvitation(theirs, "strani@primer.rs",
                theirOwner, OrganizationRole.EDITOR));
        entityManager.clear();

        mvc.perform(as(delete("/organizations/inv-mine/invitations/" + theirRow.getId()), owner))
                .andExpect(status().isNotFound());
        mvc.perform(as(post("/organizations/inv-mine/invitations/" + theirRow.getId() + "/dismiss"), owner))
                .andExpect(status().isNotFound());

        assertThat(reread(theirRow.getId()).getStatus()).isEqualTo(InvitationStatus.WAITING);
    }

    @Test
    void acceptingAsAMemberIs409AlreadyMemberAndTheRoleStays() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-already"));
        User owner = member(organization, "alreadyowner", OrganizationRole.OWNER);
        User me = member(organization, "alreadyme", OrganizationRole.EDITOR);
        OrganizationInvitation invitation = stored(TestFixtures.addressInvitation(organization,
                "alreadyme@example.com", owner, OrganizationRole.RECRUITER));
        entityManager.clear();

        mvc.perform(as(post("/invitations/" + invitation.getId() + "/accept"), me))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/already-member"));

        assertThat(roles.findLiveRole(organization.getId(), me.getId())).contains(OrganizationRole.EDITOR);
    }

    @Test
    void anInvitationWhoseInviterIsGoneServesNoInviter() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-gone"));
        User owner = member(organization, "goneowner", OrganizationRole.OWNER);
        User admin = member(organization, "goneadmin", OrganizationRole.ADMIN);
        User me = verified("goneme");
        OrganizationInvitation invitation = stored(TestFixtures.invitation(organization, me, admin,
                OrganizationRole.EDITOR));
        entityManager.clear();
        users.deleteById(admin.getId());
        entityManager.flush();
        entityManager.clear();

        mvc.perform(as(get("/organizations/inv-gone/invitations"), owner))
                .andExpect(jsonPath("$[0].id").value(invitation.getId().toString()))
                .andExpect(jsonPath("$[0].invitedByUsername").value(nullValue()));
        mvc.perform(as(get("/users/goneme/invitations"), me))
                .andExpect(jsonPath("$[0].invitedBy").value(nullValue()));
    }

    @Test
    void verifyingARegisteredAddressBindsWhatWaitsForItAndRegisteringAloneDoesNot() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-register"));
        User owner = member(organization, "registerowner", OrganizationRole.OWNER);
        OrganizationInvitation invitation = stored(TestFixtures.addressInvitation(organization,
                "novi.clan@primer.rs", owner, OrganizationRole.EDITOR));
        entityManager.clear();

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "noviclan", "email": "Novi.Clan@primer.rs", "password": "Lozinka123", "fullName": "",
                 "bio": null, "avatar": null, "location": null, "otherContactInfo": []}"""))
                .andExpect(status().isCreated());
        assertThat(reread(invitation.getId()).getInvitee()).isNull();

        User registered = users.findByUsername("noviclan").orElseThrow();
        rewriteLiveLink(registered, "bind-on-signup");
        mvc.perform(verify("bind-on-signup")).andExpect(status().isNoContent());

        OrganizationInvitation bound = reread(invitation.getId());
        assertThat(bound.getInvitee().getId()).isEqualTo(registered.getId());
        assertThat(bound.getStatus()).isEqualTo(InvitationStatus.WAITING);
        mvc.perform(as(get("/users/noviclan/invitations"), registered))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(invitation.getId().toString()));
    }

    @Test
    void confirmingAChangeOfAddressBindsWhatWaitsForTheNewAddress() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-change"));
        User owner = member(organization, "changeowner", OrganizationRole.OWNER);
        User me = withPassword("changeme");
        OrganizationInvitation invitation = stored(TestFixtures.addressInvitation(organization,
                "nova.adresa@primer.rs", owner, OrganizationRole.EDITOR));
        entityManager.clear();

        requestAddress(me, "nova.adresa@primer.rs");
        assertThat(reread(invitation.getId()).getInvitee()).isNull();

        confirmAddress(me);

        assertThat(reread(invitation.getId()).getInvitee().getId()).isEqualTo(me.getId());
    }

    @Test
    void bindingKeepsAnOlderUsernameRowAndCancelsTheNewerAddressRow() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-twin-a"));
        User owner = member(organization, "twinaowner", OrganizationRole.OWNER);
        User me = withPassword("twiname");
        OrganizationInvitation byUsername = stored(TestFixtures.invitation(organization, me, owner,
                OrganizationRole.EDITOR));
        OrganizationInvitation byAddress = stored(TestFixtures.addressInvitation(organization, "twin.a@primer.rs",
                owner, OrganizationRole.ADMIN));
        created(byUsername, 4);
        created(byAddress, 2);
        entityManager.clear();

        requestAddress(me, "twin.a@primer.rs");
        confirmAddress(me);

        OrganizationInvitation kept = reread(byUsername.getId());
        assertThat(kept.getStatus()).isEqualTo(InvitationStatus.WAITING);
        OrganizationInvitation dropped = reread(byAddress.getId());
        assertThat(dropped.getStatus()).isEqualTo(InvitationStatus.CANCELED);
        assertThat(dropped.getInvitee()).isNull();
    }

    @Test
    void bindingKeepsAnOlderAddressRowAndCancelsTheNewerUsernameRow() throws Exception {
        Organization organization = organizations.save(TestFixtures.organization("inv-twin-b"));
        User owner = member(organization, "twinbowner", OrganizationRole.OWNER);
        User me = withPassword("twinbme");
        OrganizationInvitation byAddress = stored(TestFixtures.addressInvitation(organization, "twin.b@primer.rs",
                owner, OrganizationRole.ADMIN));
        OrganizationInvitation byUsername = stored(TestFixtures.invitation(organization, me, owner,
                OrganizationRole.EDITOR));
        created(byAddress, 4);
        created(byUsername, 2);
        entityManager.clear();

        requestAddress(me, "twin.b@primer.rs");
        confirmAddress(me);

        OrganizationInvitation dropped = reread(byUsername.getId());
        assertThat(dropped.getStatus()).isEqualTo(InvitationStatus.CANCELED);
        OrganizationInvitation kept = reread(byAddress.getId());
        assertThat(kept.getStatus()).isEqualTo(InvitationStatus.WAITING);
        assertThat(kept.getInvitee().getId()).isEqualTo(me.getId());
        mvc.perform(as(get("/users/twinbme/invitations"), me))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    private User verified(String username) {
        User user = TestFixtures.user(username);
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private User withPassword(String username) {
        User user = TestFixtures.user(username);
        user.setPasswordDigest(passwords.encode(PASSWORD));
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private User member(Organization organization, String username, OrganizationRole role) {
        User user = verified(username);
        join(user, organization, role);
        return user;
    }

    private void join(User user, Organization organization, OrganizationRole role) {
        members.saveAndFlush(TestFixtures.membership(user, organization));
        roles.saveAndFlush(TestFixtures.roleAssignment(user, organization, role));
    }

    private OrganizationInvitation stored(OrganizationInvitation invitation) {
        return invitations.saveAndFlush(invitation);
    }

    private void created(OrganizationInvitation invitation, int daysAgo) {
        entityManager.createNativeQuery("update organization_invitations"
                + " set created_at = now() - make_interval(days => :days) where id = :id")
                .setParameter("days", daysAgo)
                .setParameter("id", invitation.getId())
                .executeUpdate();
    }

    private long count(Organization organization) {
        entityManager.flush();
        return ((Number) entityManager.createNativeQuery("select count(*) from organization_invitations"
                + " where organization_id = :id").setParameter("id", organization.getId()).getSingleResult())
                .longValue();
    }

    /** The request writes through the same persistence context, so a read has to come off the row. */
    private OrganizationInvitation reread(UUID id) {
        entityManager.flush();
        entityManager.clear();
        return invitations.findById(id).orElseThrow();
    }

    private void requestAddress(User user, String email) throws Exception {
        mvc.perform(as(post("/users/" + user.getUsername() + "/email"), user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
    }

    private void confirmAddress(User user) throws Exception {
        rewriteLiveLink(user, "change-" + user.getUsername());
        mvc.perform(verify("change-" + user.getUsername())).andExpect(status().isNoContent());
    }

    /** Only the digest is stored, so the live row is pointed at a plaintext the test chose. */
    private void rewriteLiveLink(User user, String plaintext) {
        entityManager.createNativeQuery("update email_verification_tokens set token_hash = :hash"
                + " where user_id = :id and consumed_at is null")
                .setParameter("hash", SecureTokens.hash(plaintext))
                .setParameter("id", user.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private MockHttpServletRequestBuilder invite(User caller, String slug, String identifier, String role) {
        return as(post("/organizations/" + slug + "/invitations"), caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + identifier + "\",\"role\":\"" + role + "\"}");
    }

    private static MockHttpServletRequestBuilder verify(String token) {
        return post("/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}");
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(user));
    }
}
