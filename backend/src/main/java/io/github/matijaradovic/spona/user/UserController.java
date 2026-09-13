package io.github.matijaradovic.spona.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.actor.ActorService;
import io.github.matijaradovic.spona.actor.dto.ProfileDto;
import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.organization.dto.MyOrganizationDto;
import io.github.matijaradovic.spona.organization.member.MembershipService;
import io.github.matijaradovic.spona.user.dto.ChangeEmailRequest;
import io.github.matijaradovic.spona.user.dto.ChangePasswordRequest;
import io.github.matijaradovic.spona.user.dto.ChangeUsernameRequest;
import io.github.matijaradovic.spona.user.dto.DeleteAccountRequest;
import io.github.matijaradovic.spona.user.dto.UpdateProfileRequest;
import io.github.matijaradovic.spona.user.dto.UserSettingsDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService usersService;
    private final ActorService actors;
    private final MembershipService memberships;

    public UserController(UserService usersService, ActorService actors, MembershipService memberships) {
        this.usersService = usersService;
        this.actors = actors;
        this.memberships = memberships;
    }

    @GetMapping
    boolean isUsernameAvailable(@RequestParam String username) {
        return usersService.isUsernameAvailable(username);
    }

    @GetMapping("/{username}")
    ProfileDto profile(@PathVariable String username, @CurrentUser User viewer) {
        return actors.userProfile(username, viewer);
    }

    @GetMapping("/{username}/settings")
    UserSettingsDto settings(@PathVariable String username, @CurrentUser User caller) {
        return usersService.settings(self(username, caller));
    }

    @PutMapping("/{username}")
    UserSettingsDto updateProfile(@PathVariable String username, @CurrentUser User caller,
            @Valid @RequestBody UpdateProfileRequest request) {
        return usersService.updateProfile(self(username, caller), request);
    }

    @PutMapping("/{username}/username")
    UserSettingsDto changeUsername(@PathVariable String username, @CurrentUser User caller,
            @Valid @RequestBody ChangeUsernameRequest request) {
        return usersService.changeUsername(self(username, caller), request.username());
    }

    @PostMapping("/{username}/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeEmail(@PathVariable String username, @CurrentUser User caller,
            @Valid @RequestBody ChangeEmailRequest request) {
        usersService.requestEmailChange(self(username, caller), request);
    }

    @PutMapping("/{username}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@PathVariable String username, @CurrentUser User caller,
            @Valid @RequestBody ChangePasswordRequest request) {
        usersService.changePassword(self(username, caller), request);
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(@PathVariable String username, @CurrentUser User caller,
            @Valid @RequestBody DeleteAccountRequest request) {
        usersService.deleteAccount(self(username, caller), request.password());
    }

    @GetMapping("/{username}/organizations")
    List<MyOrganizationDto> organizations(@PathVariable String username, @CurrentUser User caller) {
        return memberships.organizations(self(username, caller));
    }

    @DeleteMapping("/{username}/organizations/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@PathVariable String username, @PathVariable String slug, @CurrentUser User caller) {
        memberships.leave(slug, self(username, caller));
    }

    private static User self(String username, User caller) {
        if (caller == null || !caller.getUsername().equals(username)) {
            throw new ForbiddenException("This is not your account.");
        }
        return caller;
    }
}
