package io.github.matijaradovic.spona.auth;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.auth.token.PasswordResetToken;
import io.github.matijaradovic.spona.auth.token.PasswordResetTokenRepository;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.SecureTokens;
import io.github.matijaradovic.spona.common.util.Strings;
import io.github.matijaradovic.spona.common.util.TransactionHooks;
import io.github.matijaradovic.spona.config.props.AppProperties;
import io.github.matijaradovic.spona.mail.MailService;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

@Service
public class PasswordResetService {

    private static final String TEMPLATE = "mail/password-reset";
    private static final String SUBJECT = "Reset your Spona password";
    private static final String DEAD_TOKEN = "Link is invalid or has expired.";

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final MailService mail;
    private final AppProperties app;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users,
            PasswordEncoder passwords, MailService mail, AppProperties app) {
        this.tokens = tokens;
        this.users = users;
        this.passwords = passwords;
        this.mail = mail;
        this.app = app;
    }

    @Transactional
    public void request(String email) {
        users.findByEmailIgnoreCase(Strings.normalizeEmail(email)).ifPresent(this::issue);
    }

    /** spends every live link of that user in one transaction */
    @Transactional
    public void confirm(String presented, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken token = tokens.findByTokenHash(SecureTokens.hash(presented))
                .filter(row -> row.isLive(now))
                .orElseThrow(() -> UnprocessableException.on("token", DEAD_TOKEN));

        User user = token.getUser();
        user.setPasswordDigest(passwords.encode(newPassword));

        token.consume(now);
        tokens.findByUserAndConsumedAtIsNull(user).forEach(row -> row.consume(now));
    }

    private void issue(User user) {
        String plaintext = SecureTokens.newToken();
        tokens.save(new PasswordResetToken(user, SecureTokens.hash(plaintext),
                Instant.now().plus(PasswordResetToken.LIFETIME)));

        String address = user.getEmail();
        String recipient = user.resolveDisplayName();
        String url = link(plaintext);
        TransactionHooks.afterCommit(() -> mail.send(address, SUBJECT, TEMPLATE,
                Map.of("displayName", recipient, "link", url)));
    }

    private String link(String plaintext) {
        String base = app.url();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/reset/" + plaintext;
    }
}
