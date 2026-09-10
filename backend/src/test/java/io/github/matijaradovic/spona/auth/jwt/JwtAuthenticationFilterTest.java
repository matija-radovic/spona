package io.github.matijaradovic.spona.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import io.github.matijaradovic.spona.common.web.ProblemDetailWriter;
import io.github.matijaradovic.spona.config.props.JwtProperties;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

import tools.jackson.databind.json.JsonMapper;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "dev-only-secret-change-me-at-least-32-bytes";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofDays(7)));
    private final UserRepository users = mock(UserRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtService, users, new ProblemDetailWriter(JsonMapper.builder().build()));

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void letsAGuestThrough() throws Exception {
        filter.doFilter(request(null), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void authenticatesALiveVerifiedUser() throws Exception {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.userWithId("matija", id);
        user.setEmailVerifiedAt(Instant.now());
        when(users.findById(id)).thenReturn(Optional.of(user));

        filter.doFilter(request(jwtService.sign(user)), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(user);
    }

    @Test
    void rejectsAnUnusableToken() throws Exception {
        filter.doFilter(request("not.a.jwt"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** A valid signature over a row that is gone answers exactly as a bad signature does. */
    @Test
    void rejectsATokenWhoseUserIsGone() throws Exception {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.userWithId("matija", id);
        when(users.findById(any(UUID.class))).thenReturn(Optional.empty());

        filter.doFilter(request(jwtService.sign(user)), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void rejectsAnUnverifiedUserWithTheTypeLoginBranchesOn() throws Exception {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.userWithId("matija", id);
        when(users.findById(id)).thenReturn(Optional.of(user));

        filter.doFilter(request(jwtService.sign(user)), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).contains("/problems/email-not-verified");
    }

    @Test
    void ignoresAHeaderThatIsNotBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/jobs");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic bWF0aWphOmh1bnRlcjI=");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/jobs");
        if (token != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request;
    }
}
