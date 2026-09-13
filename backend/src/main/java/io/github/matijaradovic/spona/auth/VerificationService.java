package io.github.matijaradovic.spona.auth;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.auth.token.EmailVerificationPurpose;
import io.github.matijaradovic.spona.auth.token.EmailVerificationToken;
import io.github.matijaradovic.spona.auth.token.EmailVerificationTokenRepository;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.SecureTokens;
import io.github.matijaradovic.spona.common.util.TransactionHooks;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.config.props.AppProperties;
import io.github.matijaradovic.spona.mail.MailService;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

@Service
public class VerificationService {

    private static final String CHANGED_TEMPLATE = "mail/email-changed";
    private static final String CHANGED_SUBJECT = "Your email address was changed";

    private final EmailVerificationTokenRepository tokens;
    private final UserRepository users;
    private final MailService mail;
    private final AppProperties app;

    public VerificationService(EmailVerificationTokenRepository tokens, UserRepository users,
            MailService mail, AppProperties app) {
        this.tokens = tokens;
        this.users = users;
        this.mail = mail;
        this.app = app;
    }

    /**
     * Writes a live token and mails its link
     * The mail is `@Async` and since ther are no sockets/webhooks, it reports
     * nothing back
     */
    @Transactional
    public void issue(User user, EmailVerificationPurpose purpose, String email) {
        tokens.findByUserAndPurposeAndConsumedAtIsNull(user, purpose).ifPresent(previous -> {
            previous.consume(Instant.now());
            tokens.flush();
        });

        String plaintext = SecureTokens.newToken();
        tokens.save(new EmailVerificationToken(user, purpose, email, SecureTokens.hash(plaintext),
                Instant.now().plus(purpose.lifetime())));

        String recipient = user.resolveDisplayName();
        String url = link(plaintext);
        TransactionHooks.afterCommit(() -> mail.sendVerification(email, recipient, url));
    }

    /** Spends a token and applies what its purpose means */
    @Transactional
    public void confirm(String presented) {
        Instant now = Instant.now();
        EmailVerificationToken token = tokens.findByTokenHash(SecureTokens.hash(presented))
                .filter(row -> row.isLive(now))
                .orElseThrow(() -> UnprocessableException.on("token", "Link is invalid or has expired."));

        token.consume(now);
        User user = token.getUser();
        if (token.getPurpose() == EmailVerificationPurpose.CHANGE) {
            applyEmailChange(user, token.getEmail());
        }
        user.setEmailVerifiedAt(now);
    }

    /**
     * The notice goes to the address being left behind, which is what makes a
     * takeover visible.
     */
    private void applyEmailChange(User user, String requested) {
        String previous = user.getEmail();
        if (!previous.equalsIgnoreCase(requested) && users.existsByEmailIgnoreCase(requested)) {
            throw new ConflictException("That email address is already registered.", ProblemTypes.EMAIL_TAKEN);
        }

        user.setEmail(requested);
        String recipient = user.resolveDisplayName();
        TransactionHooks.afterCommit(() -> mail.send(previous, CHANGED_SUBJECT, CHANGED_TEMPLATE,
                Map.of("displayName", recipient, "newEmail", requested)));
    }

    private String link(String plaintext) {
        String base = app.url();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/verify/" + plaintext;
    }
}
