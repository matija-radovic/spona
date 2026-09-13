package io.github.matijaradovic.spona.organization.invitation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.user.User;

@RestController
@RequestMapping("/invitations/{id}")
public class InvitationController {

    private final InvitationService invitations;

    public InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(@PathVariable UUID id, @CurrentUser User caller) {
        invitations.accept(id, caller);
    }

    @PostMapping("/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@PathVariable UUID id, @CurrentUser User caller) {
        invitations.decline(id, caller);
    }
}
