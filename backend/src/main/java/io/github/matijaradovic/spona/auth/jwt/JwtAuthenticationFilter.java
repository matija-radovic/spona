package io.github.matijaradovic.spona.auth.jwt;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.matijaradovic.spona.common.web.ProblemDetailWriter;
import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Turns a bearer token into an authenticated `User` in the
 * `SecurityContextHolder`, request that
 * carries no token passes through as a guest, so a public read works
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEAD_SESSION = "Session not valid. Sign in again.";
    private static final String UNVERIFIED = "Verify your email address to continue.";

    private final JwtService jwtService;
    private final UserRepository users;
    private final ProblemDetailWriter problems;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users, ProblemDetailWriter problems) {
        this.jwtService = jwtService;
        this.users = users;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = bearerToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        User user;
        try {
            Jwt jwt = jwtService.parse(token);
            user = users.findById(subjectId(jwt)).orElse(null);
        } catch (JwtException ex) {
            logger.debug("Rejected bearer token: " + ex.getMessage());
            problems.write(request, response, HttpStatus.UNAUTHORIZED, DEAD_SESSION, null);
            return;
        }

        // user deleted account - old token should be dismissed
        if (user == null) {
            problems.write(request, response, HttpStatus.UNAUTHORIZED, DEAD_SESSION, null);
            return;
        }
        if (user.getEmailVerifiedAt() == null) {
            problems.write(request, response, HttpStatus.FORBIDDEN, UNVERIFIED, ProblemTypes.EMAIL_NOT_VERIFIED);
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static UUID subjectId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BadJwtException("The sub claim is not a user id.");
        }
    }
}
