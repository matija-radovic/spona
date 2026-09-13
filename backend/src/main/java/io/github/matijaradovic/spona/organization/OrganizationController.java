package io.github.matijaradovic.spona.organization;

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

import io.github.matijaradovic.spona.actor.ActorService;
import io.github.matijaradovic.spona.actor.dto.ProfileDto;
import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.organization.dto.ChangeSlugRequest;
import io.github.matijaradovic.spona.organization.dto.CreateOrganizationRequest;
import io.github.matijaradovic.spona.organization.dto.DeleteOrganizationRequest;
import io.github.matijaradovic.spona.organization.dto.OrgSettingsDto;
import io.github.matijaradovic.spona.organization.dto.OrganizationDto;
import io.github.matijaradovic.spona.organization.dto.TransferOwnershipRequest;
import io.github.matijaradovic.spona.organization.dto.UpdateOrganizationRequest;
import io.github.matijaradovic.spona.user.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/organizations")
public class OrganizationController {

    private final ActorService actors;
    private final OrganizationService organizations;

    public OrganizationController(ActorService actors, OrganizationService organizations) {
        this.actors = actors;
        this.organizations = organizations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrganizationDto create(@CurrentUser User caller, @Valid @RequestBody CreateOrganizationRequest request) {
        return organizations.create(caller, request);
    }

    @GetMapping("/{slug}")
    ProfileDto profile(@PathVariable String slug, @CurrentUser User viewer) {
        return actors.organizationProfile(slug, viewer);
    }

    @GetMapping("/{slug}/settings")
    OrgSettingsDto settings(@PathVariable String slug, @CurrentUser User caller) {
        return organizations.settings(slug, caller);
    }

    @PutMapping("/{slug}")
    OrgSettingsDto updateProfile(@PathVariable String slug, @CurrentUser User caller,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizations.updateProfile(slug, caller, request);
    }

    @PutMapping("/{slug}/slug")
    OrgSettingsDto changeSlug(@PathVariable String slug, @CurrentUser User caller,
            @Valid @RequestBody ChangeSlugRequest request) {
        return organizations.changeSlug(slug, caller, request.slug());
    }

    @PostMapping("/{slug}/transfer-ownership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void transferOwnership(@PathVariable String slug, @CurrentUser User caller,
            @Valid @RequestBody TransferOwnershipRequest request) {
        organizations.transferOwnership(slug, caller, request);
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String slug, @CurrentUser User caller,
            @Valid @RequestBody DeleteOrganizationRequest request) {
        organizations.delete(slug, caller, request.confirmSlug());
    }
}
