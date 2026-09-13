package io.github.matijaradovic.spona.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import io.github.matijaradovic.spona.actor.privacy.PrivacySettings;
import io.github.matijaradovic.spona.actor.privacy.PrivacySettingsRepository;
import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.auth.token.EmailVerificationPurpose;
import io.github.matijaradovic.spona.auth.token.EmailVerificationTokenRepository;
import io.github.matijaradovic.spona.auth.token.PasswordResetTokenRepository;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.support.ContainersConfig;
import io.github.matijaradovic.spona.support.TestFixtures;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
@Transactional
class UserSettingsIntegrationTest {

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
    private EmailVerificationTokenRepository verificationTokens;

    @Autowired
    private PasswordResetTokenRepository resetTokens;

    @Autowired
    private PrivacySettingsRepository privacySettings;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private EntityManager entityManager;

    private String digest;

    @BeforeEach
    void hashOnce() {
        digest = passwords.encode(PASSWORD);
    }

    @Test
    void theOwnerReadsTheirSettingsAndNoPrivacyRowReadsAsShown() throws Exception {
        User self = verified("setself");

        mvc.perform(as(get("/users/setself/settings"), self))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("setself"))
                .andExpect(jsonPath("$.email").value("setself@example.com"))
                .andExpect(jsonPath("$.privacy.showJobPostings").value(true))
                .andExpect(jsonPath("$.actorType").doesNotExist());
    }

    @Test
    void anotherAccountIs403WhetherOrNotTheHandleExists() throws Exception {
        verified("setvictim");
        User stranger = verified("setstranger");

        mvc.perform(as(get("/users/setvictim/settings"), stranger)).andExpect(status().isForbidden());
        mvc.perform(as(get("/users/nepostojeci/settings"), stranger)).andExpect(status().isForbidden());
    }

    @Test
    void aGuestIs401OnTheSettingsRead() throws Exception {
        verified("setguest");

        mvc.perform(get("/users/setguest/settings")).andExpect(status().isUnauthorized());
    }

    @Test
    void aProfileUpdateIsNormalisedAndStored() throws Exception {
        User self = verified("setprofile");

        mvc.perform(json(as(put("/users/setprofile"), self), """
                {"fullName":"  Novo Ime ","bio":"   ","avatar":null,"location":" Novi Sad ",
                 "otherContactInfo":[{"type":"email","description":"Posao","value":"posao@primer.rs"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Novo Ime"))
                .andExpect(jsonPath("$.bio").value(nullValue()))
                .andExpect(jsonPath("$.location").value("Novi Sad"))
                .andExpect(jsonPath("$.privacy.showJobPostings").value(true));

        User stored = reload(self);
        assertThat(stored.getFullName()).isEqualTo("Novo Ime");
        assertThat(stored.getBio()).isNull();
        assertThat(stored.getLocation()).isEqualTo("Novi Sad");
        assertThat(stored.getOtherContactInfo()).hasSize(1);
    }

    @Test
    void aRenameMovesTheAccountAndTheSameTokenKeepsWorking() throws Exception {
        User self = verified("setrename");
        String token = jwtService.sign(self);

        mvc.perform(json(bearer(put("/users/setrename/username"), token), "{\"username\":\"setrenamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("setrenamed"));
        entityManager.flush();

        mvc.perform(bearer(get("/users/setrenamed/settings"), token)).andExpect(status().isOk());
        mvc.perform(bearer(get("/users/setrename/settings"), token)).andExpect(status().isForbidden());
    }

    @Test
    void aRenameToAReservedHandleIs409() throws Exception {
        User self = verified("setreserved");

        mvc.perform(json(as(put("/users/setreserved/username"), self), "{\"username\":\"settings\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/handle-reserved"));
    }

    @Test
    void aRenameToAHeldHandleIs409() throws Exception {
        User self = verified("settaker");
        verified("setheldname");

        mvc.perform(json(as(put("/users/settaker/username"), self), "{\"username\":\"setheldname\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/username-taken"));
    }

    @Test
    void aRenameToTheCurrentHandleIs200() throws Exception {
        User self = verified("setsame");

        mvc.perform(json(as(put("/users/setsame/username"), self), "{\"username\":\"setsame\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("setsame"));
    }

    @Test
    void anEmailChangeIsARequestAndLeavesTheAddressAlone() throws Exception {
        User self = verified("setemail");

        mvc.perform(json(as(post("/users/setemail/email"), self),
                "{\"email\":\"Novi.Mejl@Primer.rs\",\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        User stored = reload(self);
        assertThat(stored.getEmail()).isEqualTo("setemail@example.com");
        assertThat(verificationTokens.findByUserAndPurposeAndConsumedAtIsNull(stored, EmailVerificationPurpose.CHANGE))
                .hasValueSatisfying(row -> assertThat(row.getEmail()).isEqualTo("novi.mejl@primer.rs"));
    }

    @Test
    void anEmailChangeWithTheWrongPasswordIs403AndIssuesNothing() throws Exception {
        User self = verified("setemailwrong");

        mvc.perform(json(as(post("/users/setemailwrong/email"), self),
                "{\"email\":\"novi@primer.rs\",\"currentPassword\":\"pogresna\"}"))
                .andExpect(status().isForbidden());

        assertThat(verificationTokens.findByUserAndPurposeAndConsumedAtIsNull(self, EmailVerificationPurpose.CHANGE))
                .isEmpty();
    }

    @Test
    void anEmailChangeToARegisteredAddressIs409() throws Exception {
        User self = verified("setemailtaken");
        verified("setemailholder");

        mvc.perform(json(as(post("/users/setemailtaken/email"), self),
                "{\"email\":\"setemailholder@example.com\",\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/email-taken"));
    }

    @Test
    void aPasswordChangeRewritesTheDigestAndSpendsLiveResetLinks() throws Exception {
        User self = verified("setpassword");
        resetTokens.saveAndFlush(TestFixtures.resetToken(self, "plain-reset-link"));

        mvc.perform(json(as(put("/users/setpassword/password"), self),
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"NovaLozinka1\"}"))
                .andExpect(status().isNoContent());

        User stored = reload(self);
        assertThat(passwords.matches("NovaLozinka1", stored.getPasswordDigest())).isTrue();
        assertThat(resetTokens.findByUserAndConsumedAtIsNull(stored)).isEmpty();
    }

    @Test
    void aPasswordChangeWithTheWrongPasswordIs403() throws Exception {
        User self = verified("setpasswordwrong");

        mvc.perform(json(as(put("/users/setpasswordwrong/password"), self),
                "{\"currentPassword\":\"pogresna\",\"newPassword\":\"NovaLozinka1\"}"))
                .andExpect(status().isForbidden());

        assertThat(passwords.matches(PASSWORD, reload(self).getPasswordDigest())).isTrue();
    }

    @Test
    void deletingAnAccountTakesTheOrganizationsItOwnsAndLeavesTheOnesItJoined() throws Exception {
        User leaving = verified("setleaving");
        User otherOwner = verified("setotherowner");
        Organization owned = organizations.save(TestFixtures.organization("set-owned-org"));
        Organization joined = organizations.save(TestFixtures.organization("set-joined-org"));
        member(leaving, owned, OrganizationRole.OWNER);
        member(otherOwner, joined, OrganizationRole.OWNER);
        member(leaving, joined, OrganizationRole.EDITOR);
        privacySettings.saveAndFlush(new PrivacySettings(leaving.getId()));
        entityManager.clear();

        mvc.perform(json(as(delete("/users/setleaving"), leaving), "{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        assertThat(users.findById(leaving.getId())).isEmpty();
        assertThat(count("select count(*) from actor where id = :id", leaving.getId())).isZero();
        assertThat(count("select count(*) from actor where id = :id", owned.getId())).isZero();
        assertThat(count("select count(*) from organization_member where organization_id = :id", owned.getId()))
                .isZero();
        assertThat(organizations.findById(joined.getId())).isPresent();
        assertThat(count("select count(*) from organization_member where user_id = :id", leaving.getId()))
                .isZero();
        assertThat(roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(joined.getId(), otherOwner.getId()))
                .isPresent();
        assertThat(privacySettings.findById(leaving.getId())).isEmpty();
    }

    @Test
    void deletingWithTheWrongPasswordIs403AndDeletesNothing() throws Exception {
        User self = verified("setdeletewrong");
        Organization owned = organizations.save(TestFixtures.organization("set-kept-org"));
        member(self, owned, OrganizationRole.OWNER);

        mvc.perform(json(as(delete("/users/setdeletewrong"), self), "{\"password\":\"pogresna\"}"))
                .andExpect(status().isForbidden());
        entityManager.flush();
        entityManager.clear();

        assertThat(users.findById(self.getId())).isPresent();
        assertThat(organizations.findById(owned.getId())).isPresent();
    }

    @Test
    void theTokenOfADeletedAccountIs401() throws Exception {
        User self = verified("setdeadtoken");
        String token = jwtService.sign(self);

        mvc.perform(json(bearer(delete("/users/setdeadtoken"), token), "{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        mvc.perform(bearer(get("/auth/me"), token)).andExpect(status().isUnauthorized());
    }

    private User verified(String username) {
        User user = TestFixtures.user(username);
        user.setPasswordDigest(digest);
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    private void member(User user, Organization organization, OrganizationRole role) {
        members.saveAndFlush(TestFixtures.membership(user, organization));
        roles.saveAndFlush(TestFixtures.roleAssignment(user, organization, role));
    }

    private User reload(User user) {
        entityManager.flush();
        entityManager.clear();
        return users.findById(user.getId()).orElseThrow();
    }

    private long count(String sql, UUID id) {
        return ((Number) entityManager.createNativeQuery(sql).setParameter("id", id).getSingleResult()).longValue();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        return bearer(request, jwtService.sign(user));
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
