package io.github.matijaradovic.spona.post;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.post.dto.CreatePostRequest;
import io.github.matijaradovic.spona.post.dto.PostDto;
import io.github.matijaradovic.spona.post.dto.UpdatePostRequest;
import io.github.matijaradovic.spona.user.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/{postId}")
    PostDto post(@PathVariable UUID postId, @CurrentUser User viewer) {
        return postService.post(postId, viewer);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PostDto create(@CurrentUser User caller, @Valid @RequestBody CreatePostRequest request) {
        return postService.create(caller, request);
    }

    @PutMapping("/{postId}")
    PostDto update(@PathVariable UUID postId, @CurrentUser User caller, @RequestBody UpdatePostRequest request) {
        return postService.update(postId, caller, request);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID postId, @CurrentUser User caller) {
        postService.delete(postId, caller);
    }
}
