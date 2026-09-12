package io.github.matijaradovic.spona.organization;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.actor.ActorService;
import io.github.matijaradovic.spona.actor.dto.ProfileDto;
import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.user.User;

@RestController
@RequestMapping("/organizations")
public class OrganizationController {

    private final ActorService actors;

    public OrganizationController(ActorService actors) {
        this.actors = actors;
    }

    @GetMapping("/{slug}")
    ProfileDto profile(@PathVariable String slug, @CurrentUser User viewer) {
        return actors.organizationProfile(slug, viewer);
    }
}
