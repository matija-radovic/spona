package io.github.matijaradovic.spona.auth;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.auth.dto.RegisterRequest;
import io.github.matijaradovic.spona.auth.dto.RegisterResponse;
import io.github.matijaradovic.spona.auth.token.EmailVerificationPurpose;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.util.ReservedHandles;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final VerificationService verification;
    private final PasswordEncoder passwords;

    public AuthService(UserRepository users, VerificationService verification, PasswordEncoder passwords) {
        this.users = users;
        this.verification = verification;
        this.passwords = passwords;
    }

    /** Creates the account locked, mails the link, and hands back no token */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.username();
        String email = normalize(request.email());

        if (ReservedHandles.isReserved(username)) {
            throw new ConflictException("That username is reserved.", ProblemTypes.HANDLE_RESERVED);
        }
        if (users.existsByUsername(username)) {
            throw new ConflictException("That username is taken.", ProblemTypes.USERNAME_TAKEN);
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("That email address is already registered.", ProblemTypes.EMAIL_TAKEN);
        }

        User user = new User(username, email, passwords.encode(request.password()));
        user.setFullName(trimToEmpty(request.fullName()));
        user.setBio(trimToNull(request.bio()));
        user.setAvatarUrl(trimToNull(request.avatarUrl()));
        user.setLocation(trimToNull(request.location()));
        user.setOtherContactInfo(request.otherContactInfo());
        users.saveAndFlush(user); // the token row's foreign key needs the id written first

        verification.issue(user, EmailVerificationPurpose.SIGNUP, email);
        return RegisterResponse.checkYourInbox(email);
    }

    public void verify(String token) {
        verification.confirm(token);
    }

    @Transactional
    public void resendVerification(String email) {
        users.findByEmailIgnoreCase(normalize(email))
                .filter(user -> user.getEmailVerifiedAt() == null)
                .ifPresent(user -> verification.issue(user, EmailVerificationPurpose.SIGNUP, user.getEmail()));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
