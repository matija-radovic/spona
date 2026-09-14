package io.github.matijaradovic.spona.feed;

import io.github.matijaradovic.spona.post.dto.PostDto;

public sealed interface FeedBlockDto {

    record PostBlock(String kind, PostDto post) implements FeedBlockDto {
    }

    static FeedBlockDto post(PostDto post) {
        return new PostBlock("post", post);
    }
}
