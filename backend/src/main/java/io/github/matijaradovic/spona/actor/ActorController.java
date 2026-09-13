package io.github.matijaradovic.spona.actor;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.actor.dto.PrivacyDto;
import io.github.matijaradovic.spona.actor.privacy.PrivacyService;
import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.user.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/actors")
public class ActorController {

    private final PrivacyService privacy;

    public ActorController(PrivacyService privacy) {
        this.privacy = privacy;
    }

    @PutMapping("/{actorId}/privacy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updatePrivacy(@PathVariable UUID actorId, @CurrentUser User caller,
            @Valid @RequestBody PrivacyDto request) {
        privacy.update(actorId, caller, request);
    }
}
