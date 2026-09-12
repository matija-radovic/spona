package io.github.matijaradovic.spona.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.actor.ActorService;
import io.github.matijaradovic.spona.actor.dto.ProfileDto;
import io.github.matijaradovic.spona.auth.jwt.CurrentUser;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService usersService;
    private final ActorService actors;

    public UserController(UserService usersService, ActorService actors) {
        this.usersService = usersService;
        this.actors = actors;
    }

    @GetMapping
    boolean isUsernameAvailable(@RequestParam String username) {
        return usersService.isUsernameAvailable(username);
    }

    @GetMapping("/{username}")
    ProfileDto profile(@PathVariable String username, @CurrentUser User viewer) {
        return actors.userProfile(username, viewer);
    }
}
