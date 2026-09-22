package io.github.matijaradovic.spona.actor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.dto.ActorSearchItemDto;
import io.github.matijaradovic.spona.actor.dto.ProfileDto;
import io.github.matijaradovic.spona.actor.follow.FollowRepository;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.Strings;
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
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROFILE_NOT_FOUND));

        return ProfileDto.from(subject,
                follows.countByFollowee_Id(subject.getId()),
                follows.countByFollower_Id(subject.getId()),
                viewerFieldsFor(viewer, subject));
    }

    @Transactional(readOnly = true)
    public ProfileDto organizationProfile(String slug, User viewer) {
        Organization subject = organizations.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROFILE_NOT_FOUND));

        return ProfileDto.from(subject,
                follows.countByFollowee_Id(subject.getId()),
                viewerFieldsFor(viewer, subject));
    }

    /** Handle order >> {@code q} matches name or handle, case-folded, and '' matches everyone */
    @Transactional(readOnly = true)
    public Page<ActorSearchItemDto> search(String q, String type, Pageable pageable, User viewer) {
        String query = Strings.trimToEmpty(q);
        return switch (type) {
            case "user" -> {
                Page<User> page = users.search(query, pageable);
                List<UUID> ids = page.map(User::getId).getContent();
                Map<UUID, Long> followers = follows.followerCounts(ids);
                Map<UUID, Long> following = follows.followingCounts(ids);
                Set<UUID> followed = followedBy(viewer, ids);
                yield page.map(row -> ActorSearchItemDto.from(row,
                        followers.getOrDefault(row.getId(), 0L),
                        following.getOrDefault(row.getId(), 0L),
                        viewer == null ? null : followed.contains(row.getId())));
            }
            case "org" -> {
                Page<Organization> page = organizations.search(query, pageable);
                List<UUID> ids = page.map(Organization::getId).getContent();
                Map<UUID, Long> followers = follows.followerCounts(ids);
                Set<UUID> followed = followedBy(viewer, ids);
                yield page.map(row -> ActorSearchItemDto.from(row,
                        followers.getOrDefault(row.getId(), 0L),
                        viewer == null ? null : followed.contains(row.getId())));
            }
            default -> throw UnprocessableException.on("type", ErrorCode.UNKNOWN_ACTOR_TYPE);
        };
    }

    private Set<UUID> followedBy(User viewer, List<UUID> ids) {
        return viewer == null ? Set.of() : follows.followeesAmong(viewer.getId(), ids);
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
