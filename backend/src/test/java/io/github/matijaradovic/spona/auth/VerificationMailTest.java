package io.github.matijaradovic.spona.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;

import io.github.matijaradovic.spona.auth.dto.RegisterRequest;
import io.github.matijaradovic.spona.auth.token.EmailVerificationPurpose;
import io.github.matijaradovic.spona.auth.token.EmailVerificationTokenRepository;
import io.github.matijaradovic.spona.common.util.SecureTokens;
import io.github.matijaradovic.spona.config.props.AppProperties;
import io.github.matijaradovic.spona.support.ContainersConfig;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@Import(ContainersConfig.class)
@Transactional
class VerificationMailTest {

    private static final int SMTP_PORT = freePort();

    private static final Pattern LINKED_TOKEN = Pattern.compile("/verify/([A-Za-z0-9_-]{43})");

    @RegisterExtension
    static final GreenMailExtension smtp = new GreenMailExtension(ServerSetup.SMTP.port(SMTP_PORT));

    @Autowired
    private AuthService auth;

    @Autowired
    private VerificationService verification;

    @Autowired
    private UserRepository users;

    @Autowired
    private EmailVerificationTokenRepository tokens;

    @Autowired
    private AppProperties app;

    @PersistenceContext
    private EntityManager entities;

    @DynamicPropertySource
    static void pointMailAtGreenMail(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
    }

    @Test
    void registrationMailsTheAddressTheLinkTheClientRouteExpects() throws Exception {
        auth.register(request("posta", "posta@example.com"));

        assertThat(smtp.waitForIncomingEmail(5_000, 1)).isTrue();

        MimeMessage message = smtp.getReceivedMessages()[0];
        assertThat(message.getAllRecipients()[0]).hasToString("posta@example.com");
        assertThat(message.getSubject()).isEqualTo("Confirm your Spona address");
        assertThat(body(message)).contains(app.url() + "/verify/");
    }

    @Test
    void theMailedTokenVerifiesWhileTheRowHoldsOnlyItsDigest() throws Exception {
        auth.register(request("krug", "krug@example.com"));
        assertThat(smtp.waitForIncomingEmail(5_000, 1)).isTrue();

        String plaintext = tokenIn(smtp.getReceivedMessages()[0]);
        User user = users.findByUsername("krug").orElseThrow();
        assertThat(tokens.findByUserAndPurposeAndConsumedAtIsNull(user, EmailVerificationPurpose.SIGNUP)
                .orElseThrow()
                .getTokenHash())
                .isEqualTo(SecureTokens.hash(plaintext))
                .isNotEqualTo(plaintext);

        verification.confirm(plaintext);
        entities.flush();
        entities.clear();

        assertThat(users.findById(user.getId()).orElseThrow().getEmailVerifiedAt()).isNotNull();
    }

    private static RegisterRequest request(String username, String email) {
        return new RegisterRequest(username, email, "Lozinka123", "", null, null, null, List.of());
    }

    private static String tokenIn(MimeMessage message) throws Exception {
        Matcher matcher = LINKED_TOKEN.matcher(body(message));
        assertThat(matcher.find()).as("the mail carries a /verify/<token> link").isTrue();
        return matcher.group(1);
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
