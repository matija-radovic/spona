package io.github.matijaradovic.spona.organization.invitation;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A bean of its own, so the cancel commits before the conflict thrown after it rolls the caller back. */
@Component
public class InvitationCanceler {

    private final OrganizationInvitationRepository invitations;

    public InvitationCanceler(OrganizationInvitationRepository invitations) {
        this.invitations = invitations;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(UUID invitationId) {
        invitations.findById(invitationId)
                .filter(invitation -> invitation.getStatus() == InvitationStatus.WAITING)
                .ifPresent(invitation -> invitation.cancel(Instant.now()));
    }
}
