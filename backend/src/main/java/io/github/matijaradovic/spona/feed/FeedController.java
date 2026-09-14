package io.github.matijaradovic.spona.feed;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.common.web.PagedResponse;
import io.github.matijaradovic.spona.user.User;

@RestController
public class FeedController {

    private static final String DEFAULT_SIZE = "" + PageMapper.DEFAULT_SIZE;

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/feed")
    PagedResponse<FeedBlockDto> feed(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) int size, @CurrentUser User caller) {
        return PageMapper.toResponse(feedService.feed(caller, PageMapper.toPageable(page, size)));
    }
}
