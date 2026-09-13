package io.github.matijaradovic.spona.comment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.comment.dto.CommentContextDto;
import io.github.matijaradovic.spona.comment.dto.CommentDto;
import io.github.matijaradovic.spona.comment.dto.CreateCommentRequest;
import io.github.matijaradovic.spona.comment.dto.DeleteCommentResultDto;
import io.github.matijaradovic.spona.comment.dto.UpdateCommentRequest;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.common.web.PagedResponse;
import io.github.matijaradovic.spona.user.User;

import jakarta.validation.Valid;

@RestController
public class CommentController {

    private static final String DEFAULT_SIZE = "" + PageMapper.DEFAULT_SIZE;

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/posts/{postId}/comments")
    PagedResponse<CommentDto> thread(@PathVariable UUID postId, @RequestParam(defaultValue = "top") String sort,
            @RequestParam(required = false) UUID exclude, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) int size, @CurrentUser User viewer) {
        return PageMapper.toResponse(commentService.thread(postId, sort, exclude, PageMapper.toPageable(page, size),
                viewer));
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentDto create(@PathVariable UUID postId, @CurrentUser User caller,
            @Valid @RequestBody CreateCommentRequest request) {
        return commentService.create(postId, caller, request);
    }

    @GetMapping({ "/comments/{commentId}", "/posts/{postId}/comments/{commentId}" })
    CommentDto comment(@PathVariable UUID commentId, @PathVariable(required = false) UUID postId,
            @CurrentUser User viewer) {
        return commentService.comment(commentId, postId, viewer);
    }

    @GetMapping(value = { "/comments/{commentId}", "/posts/{postId}/comments/{commentId}" }, params = "context=1")
    CommentContextDto context(@PathVariable UUID commentId, @PathVariable(required = false) UUID postId,
            @CurrentUser User viewer) {
        return commentService.context(commentId, postId, viewer);
    }

    @GetMapping({ "/comments/{commentId}/responses", "/posts/{postId}/comments/{commentId}/responses" })
    PagedResponse<CommentDto> responses(@PathVariable UUID commentId, @PathVariable(required = false) UUID postId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = DEFAULT_SIZE) int size,
            @CurrentUser User viewer) {
        return PageMapper.toResponse(commentService.responses(commentId, postId, PageMapper.toPageable(page, size),
                viewer));
    }

    @PutMapping("/comments/{commentId}")
    CommentDto update(@PathVariable UUID commentId, @CurrentUser User caller,
            @RequestBody UpdateCommentRequest request) {
        return commentService.update(commentId, caller, request);
    }

    @DeleteMapping("/comments/{commentId}")
    DeleteCommentResultDto delete(@PathVariable UUID commentId, @CurrentUser User caller) {
        return commentService.delete(commentId, caller);
    }
}
