package io.github.matijaradovic.spona.actor.follow;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.actor.ActorRepository;
import io.github.matijaradovic.spona.actor.dto.FollowActorDto;
import io.github.matijaradovic.spona.common.domain.Actor;
import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.Strings;
import io.github.matijaradovic.spona.user.User;

@Service
public class FollowService {

    private final FollowRepository follows;
    private final ActorRepository actors;

    public FollowService(FollowRepository follows, ActorRepository actors) {
        this.follows = follows;
        this.actors = actors;
    }

    @Transactional
    public void follow(UUID actorId, User caller) {
        if (actorId.equals(caller.getId())) {
            throw new UnprocessableException("You cannot follow yourself.");
        }
        requireActor(actorId);
        follows.insertIfAbsent(caller.getId(), actorId);
    }

    @Transactional
    public void unfollow(UUID actorId, User caller) {
        requireActor(actorId);
        follows.deleteEdge(caller.getId(), actorId);
    }

    @Transactional(readOnly = true)
    public Page<FollowActorDto> followers(UUID actorId, String q, Pageable pageable, User viewer) {
        requireActor(actorId);
        return rows(follows.findFollowers(actorId, Strings.trimToEmpty(q), pageable), viewer);
    }

    /** Only a user follows, so an organization's list does not exist */
    @Transactional(readOnly = true)
    public Page<FollowActorDto> following(UUID actorId, String q, Pageable pageable, User viewer) {
        Actor subject = actors.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found."));
        if (subject.getActorType() != ActorType.USER) {
            throw new NotFoundException("An organization follows nobody.");
        }
        return rows(follows.findFollowing(actorId, Strings.trimToEmpty(q), pageable), viewer);
    }

    private void requireActor(UUID actorId) {
        if (!actors.existsById(actorId)) {
            throw new NotFoundException("Actor not found.");
        }
    }

    private Page<FollowActorDto> rows(Page<? extends Actor> page, User viewer) {
        Set<UUID> followed = viewer == null ? Set.of()
                : follows.followeesAmong(viewer.getId(), page.map(Actor::getId).getContent());
        return page.map(row -> FollowActorDto.from(ActorRefDto.from(row),
                viewer == null ? null : followed.contains(row.getId())));
    }
}
