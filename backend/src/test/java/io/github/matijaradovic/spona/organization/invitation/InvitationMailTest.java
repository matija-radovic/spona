package io.github.matijaradovic.spona.organization.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;

import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.config.props.AppProperties;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationMemberRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.support.ContainersConfig;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import jakarta.mail.internet.MimeMessage;

@SpringBootTest
@Import(ContainersConfig.class)
class InvitationMailTest {

    private static final int SMTP_PORT = freePort();

    private static final String SLUG = "posta-org";

    private static final List<String> WRITTEN = List.of("pozivalac", "pozvani", "clanica");

    @RegisterExtension
    static final GreenMailExtension smtp = new GreenMailExtension(ServerSetup.SMTP.port(SMTP_PORT));

    @Autowired
    private InvitationService invitations;

    @Autowired
    private OrganizationInvitationRepository rows;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private OrganizationMemberRepository members;

    @Autowired
    private RoleAssignmentRepository roles;

    @Autowired
    private AppProperties app;

    @Autowired
    private TransactionTemplate transactions;

    private Organization organization;

    private User owner;

    @DynamicPropertySource
    static void pointMailAtGreenMail(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
    }

    @BeforeEach
    void anOrganizationAndItsOwner() {
        removeTheCommittedRows();
        organization = organizations.save(TestFixtures.organization(SLUG));
        owner = verified("pozivalac", "Petar Petrović");
        join(owner, OrganizationRole.OWNER);
    }

    @AfterEach
    void removeTheCommittedRows() {
        organizations.findBySlug(SLUG).ifPresent(organizations::delete);
        WRITTEN.forEach(username -> users.findByUsername(username).ifPresent(users::delete));
    }

    @Test
    void aUsernameInvitationGreetsTheInviteeAtTheAddressOnTheirAccount() throws Exception {
        verified("pozvani", "Jovana Jovanović");

        invitations.create(SLUG, owner, "pozvani", OrganizationRole.EDITOR);

        MimeMessage message = received();
        assertThat(message.getAllRecipients()[0]).hasToString("pozvani@example.com");
        assertThat(message.getSubject()).isEqualTo("Display posta-org vas poziva da se pridružite");
        assertThat(body(message))
                .contains("Jovana Jovanović")
                .contains("Petar Petrović (@pozivalac)")
                .contains("u ulozi <span>Urednik</span>")
                .contains(app.link("/invitations"))
                .doesNotContain("Ova pozivnica je poslata")
                .doesNotContain("/register");
    }

    @Test
    void anAddressWithAnAccountGreetsItsHolderAndSaysTheAddressWasTyped() throws Exception {
        verified("pozvani", "Jovana Jovanović");

        invitations.create(SLUG, owner, "Pozvani@Example.com", OrganizationRole.RECRUITER);

        MimeMessage message = received();
        assertThat(message.getAllRecipients()[0]).hasToString("pozvani@example.com");
        assertThat(message.getSubject()).isEqualTo("Display posta-org vas poziva da se pridružite");
        assertThat(body(message))
                .contains("Jovana Jovanović")
                .contains("Ova pozivnica je poslata na vašu email adresu (<span>pozvani@example.com</span>)")
                .contains("u ulozi <span>Regruter</span>")
                .contains(app.link("/invitations"))
                .doesNotContain("/register");
    }

    @Test
    void anAddressWithNoAccountAsksForAnAccountOnThatAddress() throws Exception {
        invitations.create(SLUG, owner, "neko.novi@primer.rs", OrganizationRole.PUBLISHER);

        MimeMessage message = received();
        assertThat(message.getAllRecipients()[0]).hasToString("neko.novi@primer.rs");
        assertThat(message.getSubject()).isEqualTo("Pozvani ste u organizaciju Display posta-org");
        assertThat(body(message))
                .contains("Petar Petrović (@pozivalac)")
                .contains("u ulozi <span>Urednik i regruter</span>")
                .contains("sa OVOM email adresom (<span>neko.novi@primer.rs</span>)")
                .contains(app.link("/register"))
                .contains(app.link("/invitations"));
    }

    @Test
    void anInvitationThatRollsBackMailsNothing() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            invitations.create(SLUG, owner, "neko.novi@primer.rs", OrganizationRole.EDITOR);
            throw new IllegalStateException("rolled back on purpose");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(smtp.waitForIncomingEmail(1_000, 1)).isFalse();
        assertThat(rows.findAll()).noneMatch(row -> "neko.novi@primer.rs".equals(row.getInviteeEmail()));
    }

    @Test
    void acceptingAsAMemberIs409AndTheCancelOutlivesTheRollback() {
        User member = verified("clanica", "");
        join(member, OrganizationRole.EDITOR);
        UUID id = invitations.create(SLUG, owner, "clanica@example.com", OrganizationRole.RECRUITER).id();
        assertThat(smtp.waitForIncomingEmail(5_000, 1)).isTrue();

        assertThatThrownBy(() -> invitations.accept(id, member)).isInstanceOfSatisfying(ConflictException.class,
                ex -> assertThat(ex.getType()).isEqualTo(ProblemTypes.ALREADY_MEMBER));

        OrganizationInvitation row = rows.findById(id).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(InvitationStatus.CANCELED);
        assertThat(row.getResolvedAt()).isNotNull();
        assertThat(roles.findLiveRole(organization.getId(), member.getId())).contains(OrganizationRole.EDITOR);
    }

    private User verified(String username, String fullName) {
        User user = TestFixtures.user(username);
        user.setFullName(fullName);
        user.setEmailVerifiedAt(Instant.now());
        return users.save(user);
    }

    private void join(User user, OrganizationRole role) {
        members.save(TestFixtures.membership(user, organization));
        roles.save(TestFixtures.roleAssignment(user, organization, role));
    }

    private static MimeMessage received() {
        assertThat(smtp.waitForIncomingEmail(5_000, 1)).isTrue();
        return smtp.getReceivedMessages()[0];
    }

    private static String body(MimeMessage message) throws Exception {
        return (String) message.getContent();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
