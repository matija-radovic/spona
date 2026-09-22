package io.github.matijaradovic.spona.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.auth.dto.AuthResponse;
import io.github.matijaradovic.spona.auth.dto.LoginRequest;
import io.github.matijaradovic.spona.auth.dto.RegisterRequest;
import io.github.matijaradovic.spona.auth.dto.RegisterResponse;
import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.auth.token.EmailVerificationPurpose;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.UnauthorizedException;
import io.github.matijaradovic.spona.common.util.ReservedHandles;
import io.github.matijaradovic.spona.common.util.SecureTokens;
import io.github.matijaradovic.spona.common.util.Strings;
import io.github.matijaradovic.spona.storage.AvatarService;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;
import io.github.matijaradovic.spona.user.dto.UserDto;

@Service
public class AuthService {

    private final UserRepository users;
    private final VerificationService verification;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final AvatarService avatars;

    /** What an unknown identifier is compared against, so a miss costs a hashing round too. */
    private final String absentUserDigest;

    public AuthService(UserRepository users, VerificationService verification, PasswordEncoder passwords,
            JwtService jwt, AvatarService avatars) {
        this.users = users;
        this.verification = verification;
        this.passwords = passwords;
        this.jwt = jwt;
        this.avatars = avatars;
        this.absentUserDigest = passwords.encode(SecureTokens.newToken());
    }

    /** Creates the account locked, mails the link, and hands back no token */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.username();
        String email = Strings.normalizeEmail(request.email());

        if (ReservedHandles.isReserved(username)) {
            throw new ConflictException(ErrorCode.USERNAME_RESERVED);
        }
        if (users.existsByUsername(username)) {
            throw new ConflictException(ErrorCode.USERNAME_TAKEN);
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(ErrorCode.EMAIL_TAKEN);
        }

        User user = new User(username, email, passwords.encode(request.password()));
        user.setFullName(Strings.trimToEmpty(request.fullName()));
        user.setBio(Strings.trimToNull(request.bio()));
        user.setLocation(Strings.trimToNull(request.location()));
        user.setOtherContactInfo(request.otherContactInfo());
        if (request.avatar() != null) {
            user.setAvatarUrl(avatars.store(request.avatar()));
        }
        users.saveAndFlush(user); // the token row's foreign key needs the id written first

        verification.issue(user, EmailVerificationPurpose.SIGNUP, email);
        return RegisterResponse.checkYourInbox(email);
    }

    public AuthResponse login(LoginRequest request) {
        String identifier = request.identifier().trim();
        User user = users.findByUsername(identifier)
                .or(() -> users.findByEmailIgnoreCase(Strings.normalizeEmail(identifier)))
                .orElse(null);

        // the compare runs on a miss too, so an unknown identifier is not the faster answer
        boolean correct = passwords.matches(request.password(),
                user != null ? user.getPasswordDigest() : absentUserDigest);
        if (user == null || !correct) {
            throw new UnauthorizedException(ErrorCode.BAD_CREDENTIALS);
        }
        if (user.getEmailVerifiedAt() == null) {
            verification.issue(user, EmailVerificationPurpose.SIGNUP, user.getEmail());
            throw new ForbiddenException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        return new AuthResponse(jwt.sign(user), UserDto.from(user));
    }

    public void verify(String token) {
        verification.confirm(token);
    }

    @Transactional
    public void resendVerification(String email) {
        users.findByEmailIgnoreCase(Strings.normalizeEmail(email))
                .filter(user -> user.getEmailVerifiedAt() == null)
                .ifPresent(user -> verification.issue(user, EmailVerificationPurpose.SIGNUP, user.getEmail()));
    }

}
