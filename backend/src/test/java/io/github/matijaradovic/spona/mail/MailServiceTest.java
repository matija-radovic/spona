package io.github.matijaradovic.spona.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

import io.github.matijaradovic.spona.config.props.MailProperties;
import jakarta.mail.internet.MimeMessage;

class MailServiceTest {

    private static final String FROM = "no-reply@spona.local";
    private static final String LINK = "http://localhost:5173/verify?token=abc123";

    @RegisterExtension
    static final GreenMailExtension smtp = new GreenMailExtension(ServerSetup.SMTP.dynamicPort());

    private MailService mail;

    @BeforeEach
    void setUp() {
        mail = serviceSending(smtp.getSmtp().getPort());
    }

    @Test
    void sendsToTheRequestedRecipient() throws Exception {
        mail.sendVerification("ana@example.com", "Ana", LINK);

        MimeMessage message = received();
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(message.getAllRecipients()[0]).hasToString("ana@example.com");
    }

    @Test
    void sendsFromTheConfiguredAddress() throws Exception {
        mail.sendVerification("ana@example.com", "Ana", LINK);

        assertThat(received().getFrom()[0]).hasToString(FROM);
    }

    @Test
    void carriesTheVerificationSubject() throws Exception {
        mail.sendVerification("ana@example.com", "Ana", LINK);

        assertThat(received().getSubject()).isEqualTo("Confirm your Spona address");
    }

    @Test
    void bodyIsHtml() throws Exception {
        mail.sendVerification("ana@example.com", "Ana", LINK);

        assertThat(received().getContentType()).contains("text/html");
    }

    @Test
    void bodyCarriesTheLink() throws Exception {
        mail.sendVerification("ana@example.com", "Ana", LINK);

        assertThat(body()).contains(LINK);
    }

    @Test
    void bodyCarriesTheDisplayName() throws Exception {
        mail.sendVerification("ana@example.com", "Matija Radovic", LINK);

        assertThat(body()).contains("Matija Radovic");
    }

    @Test
    void generalSendChoosesItsOwnTemplateAndSubject() throws Exception {
        mail.send("marko@example.com", "Something else", "mail/verification",
                Map.of("displayName", "Marko", "link", LINK));

        assertThat(received().getSubject()).isEqualTo("Something else");
        assertThat(body()).contains("Marko").contains(LINK);
    }

    @Test
    void anUnreachableServerDoesNotThrow() {
        MailService offline = serviceSending(1);

        assertThatCode(() -> offline.sendVerification("ana@example.com", "Ana", LINK))
                .doesNotThrowAnyException();
    }

    private static MailService serviceSending(int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(port);

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        // SpringTemplateEngine and not the bare one, SpBoot's starter leaves OGNL off
        // the classpath because this engine evaluates ${...} with SpEL instead.
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        return new MailService(sender, engine, new MailProperties(FROM));
    }

    private static MimeMessage received() {
        assertThat(smtp.waitForIncomingEmail(2_000, 1)).isTrue();

        MimeMessage[] messages = smtp.getReceivedMessages();
        assertThat(messages).hasSize(1);
        return messages[0];
    }

    private static String body() {
        return GreenMailUtil.getBody(received());
    }
}
