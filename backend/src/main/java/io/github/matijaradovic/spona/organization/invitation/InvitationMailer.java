package io.github.matijaradovic.spona.organization.invitation;

import java.util.HashMap;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import io.github.matijaradovic.spona.config.props.AppProperties;
import io.github.matijaradovic.spona.mail.MailService;
import io.github.matijaradovic.spona.organization.member.OrganizationRole;
import io.github.matijaradovic.spona.user.UserRepository;

@Component
public class InvitationMailer {

    private static final String BY_USERNAME = "mail/invitation-username";
    private static final String BY_ADDRESS = "mail/invitation-address";
    private static final String TO_REGISTER = "mail/invitation-register";

    private final UserRepository users;
    private final MailService mail;
    private final AppProperties app;

    public InvitationMailer(UserRepository users, MailService mail, AppProperties app) {
        this.users = users;
        this.mail = mail;
        this.app = app;
    }

    public void toUser(String email, String displayName, Offer offer) {
        Map<String, Object> variables = variables(offer, email);
        variables.put("displayName", displayName);
        mail.send(email, joinSubject(offer), BY_USERNAME, variables);
    }

    /** Off the request thread */
    @Async
    public void toAddress(String email, Offer offer) {
        Map<String, Object> variables = variables(offer, email);
        users.findByEmailIgnoreCase(email).ifPresentOrElse(holder -> {
            variables.put("displayName", holder.resolveDisplayName());
            mail.send(email, joinSubject(offer), BY_ADDRESS, variables);
        }, () -> {
            variables.put("registerLink", app.link("/register"));
            mail.send(email, "Pozvani ste u organizaciju " + offer.organization(), TO_REGISTER, variables);
        });
    }

    private Map<String, Object> variables(Offer offer, String email) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("organization", offer.organization());
        variables.put("inviter", offer.inviterName() + " (@" + offer.inviterUsername() + ")");
        variables.put("role", label(offer.role()));
        variables.put("email", email);
        variables.put("link", app.link("/invitations"));
        return variables;
    }

    private static String joinSubject(Offer offer) {
        return offer.organization() + " vas poziva da se pridružite";
    }

    private static String label(OrganizationRole role) {
        return switch (role) {
            case OWNER -> "Vlasnik";
            case ADMIN -> "Administrator";
            case PUBLISHER -> "Urednik i regruter";
            case EDITOR -> "Urednik";
            case RECRUITER -> "Regruter";
        };
    }

    public record Offer(String organization, String inviterName, String inviterUsername, OrganizationRole role) {
    }
}
