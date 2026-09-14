package io.github.matijaradovic.spona.feed;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.post.Post;
import io.github.matijaradovic.spona.post.PostRepository;
import io.github.matijaradovic.spona.post.PostService;
import io.github.matijaradovic.spona.user.User;

@Service
public class FeedService {

    private final PostRepository posts;
    private final PostService postService;

    public FeedService(PostRepository posts, PostService postService) {
        this.posts = posts;
        this.postService = postService;
    }

    @Transactional(readOnly = true)
    public Page<FeedBlockDto> feed(User caller, Pageable pageable) {
        Page<Post> page = posts.findFeed(caller.getId(), pageable);
        List<FeedBlockDto> blocks = postService.dtos(page.getContent(), caller).stream()
                .map(FeedBlockDto::post)
                .toList();
        return new PageImpl<>(blocks, pageable, page.getTotalElements());
    }
}
