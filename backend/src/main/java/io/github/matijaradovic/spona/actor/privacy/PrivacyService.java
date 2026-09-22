package io.github.matijaradovic.spona.actor.privacy;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.PublishAuthority;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.user.User;

@Service
public class PrivacyService {

    private final PrivacySettingsRepository settings;
    private final ActorRepository actors;
    private final RoleAssignmentRepository roles;

    public PrivacyService(PrivacySettingsRepository settings, ActorRepository actors,
            RoleAssignmentRepository roles) {
        this.settings = settings;
        this.actors = actors;
        this.roles = roles;
    }

    /** An actor with no row has the column defaults. */
    @Transactional(readOnly = true)
    public PrivacyDto of(UUID actorId) {
        return new PrivacyDto(settings.findById(actorId)
                .map(row -> row.isShowJobPostings())
                .orElse(true));
    }

    @Transactional
    public void update(UUID actorId, User caller, PrivacyDto privacy) {
        if (!actorId.equals(caller.getId())) {
            Actor actor = actors.findById(actorId)
                    .orElseThrow(() -> new NotFoundException(ErrorCode.ACTOR_NOT_FOUND));
            if (actor.getActorType() != ActorType.ORG || !PublishAuthority.canEditProfile(roleOf(caller, actorId))) {
                throw new ForbiddenException(ErrorCode.PRIVACY_NOT_YOURS);
            }
        }

        PrivacySettings row = settings.findById(actorId).orElseGet(() -> new PrivacySettings(actorId));
        row.setShowJobPostings(privacy.showJobPostings());
        settings.save(row);
    }

    private OrganizationRole roleOf(User caller, UUID organizationId) {
        return roles.findByOrganizationIdAndUserIdAndRevokedAtIsNull(organizationId, caller.getId())
                .map(row -> row.getRole())
                .orElse(null);
    }
}
