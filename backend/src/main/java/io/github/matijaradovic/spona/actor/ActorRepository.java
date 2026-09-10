package io.github.matijaradovic.spona.actor;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.matijaradovic.spona.common.domain.Actor;

/**
 * polymorphic lookup, for everything fetching `/actors/{actorId}`, and that is
 * following, privacy, activity, and the `createdForId` on published content.
 * A find here returns the subtype instance, so a caller that needs to know
 * which one asks `getActorType()` rather than the route it arrived on.
 */
public interface ActorRepository extends JpaRepository<Actor, UUID> {
}
