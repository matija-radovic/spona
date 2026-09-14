package io.github.matijaradovic.spona.reaction;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.reaction.dto.ReactionBreakdownDto;
import io.github.matijaradovic.spona.reaction.dto.ReactionRequest;
import io.github.matijaradovic.spona.user.User;

@RestController
@RequestMapping("/reactions/{targetType}/{targetId}")
public class ReactionController {

    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @GetMapping
    ReactionBreakdownDto breakdown(@PathVariable String targetType, @PathVariable UUID targetId) {
        return reactionService.breakdown(targetType, targetId);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void react(@PathVariable String targetType, @PathVariable UUID targetId, @CurrentUser User caller,
            @RequestBody ReactionRequest request) {
        reactionService.react(targetType, targetId, caller, request.emoji());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unreact(@PathVariable String targetType, @PathVariable UUID targetId, @CurrentUser User caller,
            @RequestBody ReactionRequest request) {
        reactionService.unreact(targetType, targetId, caller, request.emoji());
    }
}
