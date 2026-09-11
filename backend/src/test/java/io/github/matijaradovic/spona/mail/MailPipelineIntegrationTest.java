package io.github.matijaradovic.spona.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

import io.github.matijaradovic.spona.support.ContainersConfig;
import jakarta.mail.internet.MimeMessage;

@SpringBootTest
@Import(ContainersConfig.class)
class MailPipelineIntegrationTest {

    private static final int SMTP_PORT = freePort();

    @RegisterExtension
    static final GreenMailExtension smtp = new GreenMailExtension(ServerSetup.SMTP.port(SMTP_PORT));

    @Autowired
    private MailService mail;

    @DynamicPropertySource
    static void pointMailAtGreenMail(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
    }

    /** Without the proxy the {@code @Async} annotation is decoration and the send is synchronous. */
    @Test
    void theServiceIsProxiedForAsync() {
        assertThat(AopUtils.isAopProxy(mail)).isTrue();
    }

    @Test
    void aVerificationMailReachesTheServer() throws Exception {
        mail.sendVerification("ana@example.com", "Ana", "http://localhost:5173/verify?token=t");

        assertThat(smtp.waitForIncomingEmail(5_000, 1)).isTrue();

        MimeMessage message = smtp.getReceivedMessages()[0];
        assertThat(message.getAllRecipients()[0]).hasToString("ana@example.com");
        assertThat(GreenMailUtil.getBody(message)).contains("token=t");
    }

    @Test
    void theSenderAddressComesFromTheConfiguredProperty() throws Exception {
        mail.sendVerification("ana@example.com", "Ana", "http://localhost:5173/verify?token=t");

        assertThat(smtp.waitForIncomingEmail(5_000, 1)).isTrue();
        assertThat(smtp.getReceivedMessages()[0].getFrom()[0]).hasToString("no-reply@spona.local");
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
