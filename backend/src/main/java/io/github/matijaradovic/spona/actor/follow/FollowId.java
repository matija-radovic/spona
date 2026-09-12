package io.github.matijaradovic.spona.actor.follow;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class FollowId implements Serializable {

    private UUID follower;
    private UUID followee;

    public FollowId() {
    }

    public FollowId(UUID follower, UUID followee) {
        this.follower = follower;
        this.followee = followee;
    }

    public UUID getFollower() {
        return follower;
    }

    public UUID getFollowee() {
        return followee;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FollowId id)) {
            return false;
        }
        return Objects.equals(follower, id.follower) && Objects.equals(followee, id.followee);
    }

    @Override
    public int hashCode() {
        return Objects.hash(follower, followee);
    }
}
