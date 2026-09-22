package io.github.matijaradovic.spona.user;

import java.time.Instant;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.privacy.PrivacyService;
import io.github.matijaradovic.spona.auth.VerificationService;
import io.github.matijaradovic.spona.auth.token.EmailVerificationPurpose;
import io.github.matijaradovic.spona.auth.token.PasswordResetTokenRepository;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.UnauthorizedException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.ReservedHandles;
import io.github.matijaradovic.spona.common.util.Strings;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.storage.AvatarService;
import io.github.matijaradovic.spona.user.dto.ChangeEmailRequest;
import io.github.matijaradovic.spona.user.dto.ChangePasswordRequest;
import io.github.matijaradovic.spona.user.dto.UpdateProfileRequest;
import io.github.matijaradovic.spona.user.dto.UserSettingsDto;

@Service
public class UserService {

    /** Mirrors the CHECK on `username`, so an unavailable answer covers a malformed handle too. */
    private static final String HANDLE_PATTERN = "^[a-z0-9_-]{3,30}$";

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final RoleAssignmentRepository roles;
    private final PasswordResetTokenRepository resetTokens;
    private final VerificationService verification;
    private final PrivacyService privacy;
    private final AvatarService avatars;
    private final PasswordEncoder passwords;

    public UserService(UserRepository users, OrganizationRepository organizations, RoleAssignmentRepository roles,
            PasswordResetTokenRepository resetTokens, VerificationService verification, PrivacyService privacy,
            AvatarService avatars, PasswordEncoder passwords) {
        this.users = users;
        this.organizations = organizations;
        this.roles = roles;
        this.resetTokens = resetTokens;
        this.verification = verification;
        this.privacy = privacy;
        this.avatars = avatars;
        this.passwords = passwords;
    }

    /** Available means registrable: legal, not reserved and not already held. */
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        String handle = username.trim();
        return handle.matches(HANDLE_PATTERN)
                && !ReservedHandles.isReserved(handle)
                && !users.existsByUsername(handle);
    }

    @Transactional(readOnly = true)
    public UserSettingsDto settings(User caller) {
        return UserSettingsDto.from(caller, privacy.of(caller.getId()));
    }

    @Transactional
    public UserSettingsDto updateProfile(User caller, UpdateProfileRequest request) {
        User user = managed(caller);
        user.setFullName(Strings.trimToEmpty(request.fullName()));
        user.setBio(Strings.trimToNull(request.bio()));
        user.setLocation(Strings.trimToNull(request.location()));
        user.setOtherContactInfo(request.otherContactInfo());
        if (request.avatar() != null) {
            user.setAvatarUrl(avatars.replace(user.getAvatarUrl(), request.avatar()));
        }
        return UserSettingsDto.from(user, privacy.of(user.getId()));
    }

    @Transactional
    public UserSettingsDto changeUsername(User caller, String username) {
        User user = managed(caller);
        if (!username.equals(user.getUsername())) {
            if (ReservedHandles.isReserved(username)) {
                throw new ConflictException(ErrorCode.USERNAME_RESERVED);
            }
            if (users.existsByUsername(username)) {
                throw new ConflictException(ErrorCode.USERNAME_TAKEN);
            }
            user.setUsername(username);
        }
        return UserSettingsDto.from(user, privacy.of(user.getId()));
    }

    /** The address moves only when the link mailed to it is confirmed */
    @Transactional
    public void requestEmailChange(User caller, ChangeEmailRequest request) {
        User user = managed(caller);
        requirePassword(user, request.currentPassword());

        String email = Strings.normalizeEmail(request.email());
        if (email.equalsIgnoreCase(user.getEmail())) {
            throw UnprocessableException.on("email", ErrorCode.EMAIL_UNCHANGED);
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(ErrorCode.EMAIL_TAKEN);
        }
        verification.issue(user, EmailVerificationPurpose.CHANGE, email);
    }

    @Transactional
    public void changePassword(User caller, ChangePasswordRequest request) {
        User user = managed(caller);
        requirePassword(user, request.currentPassword());

        user.setPasswordDigest(passwords.encode(request.newPassword()));
        Instant now = Instant.now();
        resetTokens.findByUserAndConsumedAtIsNull(user).forEach(row -> row.consume(now));
    }

    @Transactional
    public void deleteAccount(User caller, String password) {
        User user = managed(caller);
        requirePassword(user, password);

        List<Organization> owned = organizations.findAllById(
                roles.findLiveOrganizationIds(user.getId(), OrganizationRole.OWNER));

        owned.forEach(organization -> avatars.discard(organization.getAvatarUrl()));
        organizations.deleteAll(owned);
        avatars.discard(user.getAvatarUrl());
        users.delete(user);
    }

    private User managed(User caller) {
        return users.findById(caller.getId())
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.SESSION_EXPIRED));
    }

    private void requirePassword(User user, String presented) {
        if (!passwords.matches(presented, user.getPasswordDigest())) {
            throw new ForbiddenException(ErrorCode.WRONG_PASSWORD);
        }
    }
}
