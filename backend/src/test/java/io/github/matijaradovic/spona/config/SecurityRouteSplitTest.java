package io.github.matijaradovic.spona.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.support.ContainersConfig;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

/**
 * Most of these routes have no handler yet, so a 404 is the proof that matters: the request got
 * past the authorization filter and into the dispatcher.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
@Transactional
class SecurityRouteSplitTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtService jwtService;

    @Test
    void rootIsPublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void aPublicReadIsReachableWithoutAToken() throws Exception {
        mvc.perform(get("/users/matija")).andExpect(status().isNotFound());
        mvc.perform(get("/organizations/spona")).andExpect(status().isNotFound());
        mvc.perform(get("/jobs")).andExpect(status().isNotFound());
        mvc.perform(get("/posts/1/comments")).andExpect(status().isNotFound());
        mvc.perform(get("/reactions/post/1")).andExpect(status().isNotFound());
    }

    @Test
    void aPublicAuthPostIsReachableWithoutAToken() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/auth/password-reset/confirm")).andExpect(status().isNotFound());
    }

    @Test
    void aRouteUnderAPublicPrefixIsNotItselfPublic() throws Exception {
        mvc.perform(get("/users/matija/settings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/organizations")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAuthenticatedRouteAnswersAGuestWithAProblemDetail() throws Exception {
        mvc.perform(get("/feed"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason((String) null))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value("/feed"));
    }

    @Test
    void aWriteIsUnauthorizedToAGuest() throws Exception {
        mvc.perform(post("/posts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnusableTokenIsUnauthorizedEvenOnAPublicRoute() throws Exception {
        mvc.perform(get("/jobs").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnverifiedUserIsForbiddenWithTheTypeLoginBranchesOn() throws Exception {
        User user = users.save(TestFixtures.user("routeunverified"));

        mvc.perform(get("/feed").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/email-not-verified"));
    }

    @Test
    void aVerifiedUserReachesAnAuthenticatedRoute() throws Exception {
        User user = TestFixtures.user("routeverified");
        user.setEmailVerifiedAt(Instant.now());
        users.save(user);

        mvc.perform(get("/feed").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.sign(user)))
                .andExpect(status().isNotFound());
    }
}
