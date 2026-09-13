package io.github.matijaradovic.spona.actor;

import org.springframework.stereotype.Component;

import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.organization.member.PublishAuthority;
import io.github.matijaradovic.spona.organization.member.RoleAssignmentRepository;
import io.github.matijaradovic.spona.user.User;

@Component
public class ContentAuthority {

    private final RoleAssignmentRepository roles;

    public ContentAuthority(RoleAssignmentRepository roles) {
        this.roles = roles;
    }

    public boolean canManage(Actor owner, User caller) {
        if (owner.getActorType() == ActorType.USER) {
            return owner.getId().equals(caller.getId());
        }
        OrganizationRole role = roles.findLiveRole(owner.getId(), caller.getId()).orElse(null);
        return PublishAuthority.canPublishContent(role);
    }
}
