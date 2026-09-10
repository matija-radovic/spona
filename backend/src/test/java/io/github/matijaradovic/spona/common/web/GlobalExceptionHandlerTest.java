package io.github.matijaradovic.spona.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    record Body(@Size(min = 3, max = 30) String username) {
    }

    @RequestMapping("/api")
    @RestController
    static class ThrowingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("No such posting.");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException("Verify your email first.", ProblemTypes.EMAIL_NOT_VERIFIED);
        }

        @GetMapping("/forbidden-plain")
        void forbiddenPlain() {
            throw new ForbiddenException("Not yours.");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("That username is taken.", ProblemTypes.USERNAME_TAKEN);
        }

        @GetMapping("/unprocessable")
        void unprocessable() {
            throw UnprocessableException.on("username", "Between 3 and 30 characters.");
        }

        @GetMapping("/unprocessable-fieldless")
        void unprocessableFieldless() {
            throw new UnprocessableException("Nothing to say about a field.", Map.of());
        }

        @GetMapping("/check-violation")
        void checkViolation() {
            throw integrityViolation("23514", ConstraintKind.CHECK, "user_username_check");
        }

        @GetMapping("/check-violation-multi-column")
        void checkViolationMultiColumn() {
            throw integrityViolation("23514", ConstraintKind.CHECK, "job_publishable_check");
        }

        @GetMapping("/unique-violation")
        void uniqueViolation() {
            throw integrityViolation("23505", ConstraintKind.UNIQUE, "user_email_key");
        }

        @GetMapping("/unique-violation-untyped")
        void uniqueViolationUntyped() {
            throw integrityViolation("23505", ConstraintKind.UNIQUE, "role_assignment_owner_key");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody Body body) {
        }

        private static DataIntegrityViolationException integrityViolation(
                String sqlState, ConstraintKind kind, String constraint) {
            SQLException sql = new SQLException("violates constraint \"" + constraint + "\"", sqlState);
            return new DataIntegrityViolationException(
                    "could not execute statement",
                    new ConstraintViolationException("could not execute statement", sql, kind, constraint));
        }
    }

    @Test
    void notFoundIs404() throws Exception {
        mvc.perform(get("/api/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("No such posting."))
                .andExpect(jsonPath("$.instance").value("/api/not-found"));
    }

    @Test
    void forbiddenCarriesItsProblemType() throws Exception {
        mvc.perform(get("/api/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/email-not-verified"));
    }

    /** Spring omits `about:blank`, which RFC 7807 says is what an absent `type` means. */
    @Test
    void aTypelessForbiddenNamesNoProblemType() throws Exception {
        mvc.perform(get("/api/forbidden-plain"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void conflictCarriesItsProblemType() throws Exception {
        mvc.perform(get("/api/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/username-taken"));
    }

    @Test
    void unprocessableCarriesFieldErrors() throws Exception {
        mvc.perform(get("/api/unprocessable"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.username").value("Between 3 and 30 characters."));
    }

    @Test
    void aNamedCheckBecomesAFieldLevel422() throws Exception {
        mvc.perform(get("/api/check-violation"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void aCheckSpanningColumnsIs422WithNoFieldNamed() throws Exception {
        mvc.perform(get("/api/check-violation-multi-column"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void aUniqueIndexSection9NamesBecomesATyped409() throws Exception {
        mvc.perform(get("/api/unique-violation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/email-taken"));
    }

    @Test
    void aUniqueIndexSection9DoesNotNameIsAnUntyped409() throws Exception {
        mvc.perform(get("/api/unique-violation-untyped"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void beanValidationIs422AndNot400() throws Exception {
        mvc.perform(post("/api/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"a\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.instance").value("/api/validated"));
    }

    @Test
    void anUnmappedMethodIsAProblemDetailToo() throws Exception {
        mvc.perform(post("/api/not-found")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unprocessableWithoutFieldsOmitsTheErrorsMember() throws Exception {
        mvc.perform(get("/api/unprocessable-fieldless"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Nothing to say about a field."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
