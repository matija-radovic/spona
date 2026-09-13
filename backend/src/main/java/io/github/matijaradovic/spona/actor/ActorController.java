package io.github.matijaradovic.spona.actor;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.actor.activity.ActivityService;
import io.github.matijaradovic.spona.actor.activity.dto.ActivityItemDto;
import io.github.matijaradovic.spona.actor.dto.ActorSearchItemDto;
import io.github.matijaradovic.spona.actor.dto.FollowActorDto;
import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.actor.follow.FollowService;
import io.github.matijaradovic.spona.actor.privacy.PrivacyService;
import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.common.web.PagedResponse;
import io.github.matijaradovic.spona.user.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/actors")
public class ActorController {

    private static final String DEFAULT_SIZE = "" + PageMapper.DEFAULT_SIZE;

    private final PrivacyService privacy;
    private final ActorService actorsService;
    private final FollowService followService;
    private final ActivityService activityService;

    public ActorController(PrivacyService privacy, ActorService actorsService, FollowService followService,
            ActivityService activityService) {
        this.privacy = privacy;
        this.actorsService = actorsService;
        this.followService = followService;
        this.activityService = activityService;
    }

    @GetMapping
    PagedResponse<ActorSearchItemDto> search(@RequestParam(defaultValue = "") String q, @RequestParam String type,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = DEFAULT_SIZE) int size,
            @CurrentUser User viewer) {
        return PageMapper.toResponse(actorsService.search(q, type, PageMapper.toPageable(page, size), viewer));
    }

    @PutMapping("/{actorId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void follow(@PathVariable UUID actorId, @CurrentUser User caller) {
        followService.follow(actorId, caller);
    }

    @DeleteMapping("/{actorId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfollow(@PathVariable UUID actorId, @CurrentUser User caller) {
        followService.unfollow(actorId, caller);
    }

    @GetMapping("/{actorId}/followers")
    PagedResponse<FollowActorDto> followers(@PathVariable UUID actorId, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = DEFAULT_SIZE) int size,
            @CurrentUser User viewer) {
        return PageMapper.toResponse(followService.followers(actorId, q, PageMapper.toPageable(page, size), viewer));
    }

    @GetMapping("/{actorId}/following")
    PagedResponse<FollowActorDto> following(@PathVariable UUID actorId, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = DEFAULT_SIZE) int size,
            @CurrentUser User viewer) {
        return PageMapper.toResponse(followService.following(actorId, q, PageMapper.toPageable(page, size), viewer));
    }

    @GetMapping("/{actorId}/activity")
    PagedResponse<ActivityItemDto> activity(@PathVariable UUID actorId,
            @RequestParam(defaultValue = "all") String kind,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = DEFAULT_SIZE) int size,
            @CurrentUser User viewer) {
        return PageMapper.toResponse(activityService.activity(actorId, kind, PageMapper.toPageable(page, size),
                viewer));
    }

    @PutMapping("/{actorId}/privacy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updatePrivacy(@PathVariable UUID actorId, @CurrentUser User caller,
            @Valid @RequestBody PrivacyDto request) {
        privacy.update(actorId, caller, request);
    }
}
