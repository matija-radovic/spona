package io.github.matijaradovic.spona.mail;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import io.github.matijaradovic.spona.config.props.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/** Renders a Thymeleaf body and hands it to SMTP off the calling thread  */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private static final String VERIFICATION_TEMPLATE = "mail/verification";
    private static final String VERIFICATION_SUBJECT = "Confirm your Spona address";

    private final JavaMailSender sender;
    private final ITemplateEngine templates;
    private final MailProperties mail;

    public MailService(JavaMailSender sender, ITemplateEngine templates, MailProperties mail) {
        this.sender = sender;
        this.templates = templates;
        this.mail = mail;
    }

    /**
     * The general form. The template name is a path under {@code templates/} without its suffix,
     * for example {@code mail/verification}.
     */
    @Async
    public void send(String to, String subject, String template, Map<String, Object> variables) {
        dispatch(to, subject, template, variables);
    }

    /** The link is built by the caller, which owns the route it points at. */
    @Async
    public void sendVerification(String to, String displayName, String link) {
        dispatch(to, VERIFICATION_SUBJECT, VERIFICATION_TEMPLATE,
                Map.of("displayName", displayName, "link", link));
    }

    private void dispatch(String to, String subject, String template, Map<String, Object> variables) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariables(variables);
        String body = templates.process(template, context);

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mail.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            sender.send(message);
        } catch (MessagingException | MailException e) {
            log.warn("Mail '{}' was not delivered: {}", template, e.getMessage());
        }
    }
}
