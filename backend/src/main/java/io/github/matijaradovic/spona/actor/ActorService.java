package io.github.matijaradovic.spona.actor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.dto.ProfileDto;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.organization.Organization;
import io.github.matijaradovic.spona.organization.OrganizationRepository;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.PublishAuthority;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

@Service
public class ActorService {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final FollowRepository follows;
    private final RoleAssignmentRepository roles;

    public ActorService(UserRepository users, OrganizationRepository organizations,
            FollowRepository follows, RoleAssignmentRepository roles) {
        this.users = users;
        this.organizations = organizations;
        this.follows = follows;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public ProfileDto userProfile(String username, User viewer) {
        User subject = users.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Profile not found."));

        return ProfileDto.from(subject,
                follows.countByFollowee_Id(subject.getId()),
                follows.countByFollower_Id(subject.getId()),
                viewerFieldsFor(viewer, subject));
    }

    @Transactional(readOnly = true)
    public ProfileDto organizationProfile(String slug, User viewer) {
        Organization subject = organizations.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Profile not found."));

        return ProfileDto.from(subject,
                follows.countByFollowee_Id(subject.getId()),
                viewerFieldsFor(viewer, subject));
    }

    private ProfileDto.ViewerFields viewerFieldsFor(User viewer, User subject) {
        if (viewer == null) {
            return ProfileDto.ViewerFields.guest();
        }
        boolean own = viewer.getId().equals(subject.getId());
        return new ProfileDto.ViewerFields(
                follows.existsByFollower_IdAndFollowee_Id(viewer.getId(), subject.getId()),
                own,
                own);
    }

    private ProfileDto.ViewerFields viewerFieldsFor(User viewer, Organization subject) {
        if (viewer == null) {
            return ProfileDto.ViewerFields.guest();
        }
        OrganizationRole role = roles
                .findByOrganizationIdAndUserIdAndRevokedAtIsNull(subject.getId(), viewer.getId())
                .map(row -> row.getRole())
                .orElse(null);

        return new ProfileDto.ViewerFields(
                follows.existsByFollower_IdAndFollowee_Id(viewer.getId(), subject.getId()),
                PublishAuthority.canEditProfile(role),
                PublishAuthority.canManageJobPostings(role));
    }
}
