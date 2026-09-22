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

import io.github.matijaradovic.spona.common.error.BadRequestException;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnauthorizedException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    record Body(
            @NotBlank(message = "A username is required.")
            @Size(min = 3, max = 30, message = "Between 3 and 30 characters.") String username,
            @Email(message = "That is not a valid email address.") String email) {
    }

    @RequestMapping("/api")
    @RestController
    static class ThrowingController {

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BadRequestException(ErrorCode.JOB_SEARCH_TOO_LONG.with(200));
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException(ErrorCode.JOB_NOT_FOUND);
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException(ErrorCode.BAD_CREDENTIALS);
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        @GetMapping("/forbidden-plain")
        void forbiddenPlain() {
            throw new ForbiddenException(ErrorCode.JOB_NOT_EDITABLE);
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException(ErrorCode.USERNAME_TAKEN);
        }

        @GetMapping("/conflict-formatted")
        void conflictFormatted() {
            throw new ConflictException(ErrorCode.JOB_BAD_STATUS_TRANSITION.with("CLOSED", "DRAFT"));
        }

        @GetMapping("/unprocessable")
        void unprocessable() {
            throw UnprocessableException.on("username", ErrorCode.VALIDATION_SIZE.with(3, 30));
        }

        @GetMapping("/unprocessable-plain")
        void unprocessablePlain() {
            throw UnprocessableException.on("identifier", ErrorCode.NO_SUCH_USERNAME);
        }

        @GetMapping("/unprocessable-fieldless")
        void unprocessableFieldless() {
            throw new UnprocessableException(ErrorCode.SELF_FOLLOW);
        }

        @GetMapping("/unprocessable-several")
        void unprocessableSeveral() {
            throw new UnprocessableException(ErrorCode.JOB_INCOMPLETE.with(), Map.of(
                    "title", ErrorCode.JOB_TITLE_REQUIRED.with(),
                    "keywords", ErrorCode.JOB_KEYWORDS_TOO_MANY.with(10)));
        }

        @GetMapping("/check-violation")
        void checkViolation() {
            throw integrityViolation("23514", ConstraintKind.CHECK, "user_username_check");
        }

        @GetMapping("/check-violation-multi-column")
        void checkViolationMultiColumn() {
            throw integrityViolation("23514", ConstraintKind.CHECK, "job_publishable_check");
        }

        @GetMapping("/check-violation-unnamed")
        void checkViolationUnnamed() {
            throw integrityViolation("23514", ConstraintKind.CHECK, null);
        }

        @GetMapping("/unique-violation")
        void uniqueViolation() {
            throw integrityViolation("23505", ConstraintKind.UNIQUE, "user_email_key");
        }

        @GetMapping("/unique-violation-untyped")
        void uniqueViolationUntyped() {
            throw integrityViolation("23505", ConstraintKind.UNIQUE, "role_assignment_owner_key");
        }

        @GetMapping("/unique-violation-unnamed")
        void uniqueViolationUnnamed() {
            throw integrityViolation("23505", ConstraintKind.UNIQUE, null);
        }

        @GetMapping("/foreign-key-violation")
        void foreignKeyViolation() {
            throw integrityViolation("23503", ConstraintKind.FOREIGN_KEY, "post_created_for_id_fkey");
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
    void badRequestIs400WithNoFieldErrors() throws Exception {
        mvc.perform(get("/api/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("A search is at most 200 characters."))
                .andExpect(jsonPath("$.instance").value("/api/bad-request"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void aFormattedCodeServesItsArgumentsBesideTheEnglish() throws Exception {
        mvc.perform(get("/api/bad-request"))
                .andExpect(jsonPath("$.code").value("job.searchTooLong"))
                .andExpect(jsonPath("$.args.max").value(200));
    }

    @Test
    void notFoundIs404() throws Exception {
        mvc.perform(get("/api/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Job not found."))
                .andExpect(jsonPath("$.code").value("job.notFound"))
                .andExpect(jsonPath("$.instance").value("/api/not-found"));
    }

    @Test
    void aCodeThatTakesNoArgumentsServesNoArgsMember() throws Exception {
        mvc.perform(get("/api/not-found"))
                .andExpect(jsonPath("$.args").doesNotExist());
    }

    @Test
    void unauthorizedIs401WithItsCode() throws Exception {
        mvc.perform(get("/api/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong username or password."))
                .andExpect(jsonPath("$.code").value("auth.badCredentials"))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void forbiddenCarriesItsProblemType() throws Exception {
        mvc.perform(get("/api/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/email-not-verified"))
                .andExpect(jsonPath("$.code").value("auth.emailNotVerified"))
                .andExpect(jsonPath("$.detail").value("Verify your email address to continue."));
    }

    /** Spring omits `about:blank`, which RFC 7807 says is what an absent `type` means. */
    @Test
    void aTypelessForbiddenNamesNoProblemType() throws Exception {
        mvc.perform(get("/api/forbidden-plain"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.code").value("job.notEditable"));
    }

    @Test
    void conflictCarriesItsProblemType() throws Exception {
        mvc.perform(get("/api/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/username-taken"))
                .andExpect(jsonPath("$.code").value("user.usernameTaken"))
                .andExpect(jsonPath("$.detail").value("That username is taken."));
    }

    @Test
    void aFormattedConflictServesEachArgumentUnderItsName() throws Exception {
        mvc.perform(get("/api/conflict-formatted"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A posting cannot move from CLOSED to DRAFT."))
                .andExpect(jsonPath("$.code").value("job.badStatusTransition"))
                .andExpect(jsonPath("$.args.from").value("CLOSED"))
                .andExpect(jsonPath("$.args.to").value("DRAFT"));
    }

    @Test
    void unprocessableCarriesFieldErrors() throws Exception {
        mvc.perform(get("/api/unprocessable"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("validation.size"))
                .andExpect(jsonPath("$.errors.username.code").value("validation.size"))
                .andExpect(jsonPath("$.errors.username.detail").value("Between 3 and 30 characters."))
                .andExpect(jsonPath("$.errors.username.args.min").value(3))
                .andExpect(jsonPath("$.errors.username.args.max").value(30));
    }

    @Test
    void aFieldErrorWithNoArgumentsOmitsArgs() throws Exception {
        mvc.perform(get("/api/unprocessable-plain"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("No user has this username."))
                .andExpect(jsonPath("$.code").value("invitation.noSuchUsername"))
                .andExpect(jsonPath("$.errors.identifier.code").value("invitation.noSuchUsername"))
                .andExpect(jsonPath("$.errors.identifier.detail").value("No user has this username."))
                .andExpect(jsonPath("$.errors.identifier.args").doesNotExist());
    }

    @Test
    void unprocessableWithoutFieldsOmitsTheErrorsMember() throws Exception {
        mvc.perform(get("/api/unprocessable-fieldless"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("You cannot follow yourself."))
                .andExpect(jsonPath("$.code").value("follow.notYourself"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void severalFieldErrorsKeepTheirOwnCodesUnderTheWholeFormsCode() throws Exception {
        mvc.perform(get("/api/unprocessable-several"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("job.incompleteToPublish"))
                .andExpect(jsonPath("$.errors.title.code").value("job.titleRequiredToPublish"))
                .andExpect(jsonPath("$.errors.keywords.code").value("job.keywordsTooMany"))
                .andExpect(jsonPath("$.errors.keywords.detail").value("At most 10 keywords."))
                .andExpect(jsonPath("$.errors.keywords.args.max").value(10));
    }

    @Test
    void aNamedCheckBecomesAFieldLevel422() throws Exception {
        mvc.perform(get("/api/check-violation"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.errors.username.code").value("validation.invalid"))
                .andExpect(jsonPath("$.errors.username.detail").value("Invalid value."));
    }

    @Test
    void aCheckSpanningColumnsIs422WithNoFieldNamed() throws Exception {
        mvc.perform(get("/api/check-violation-multi-column"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void aCheckWithNoConstraintNameIs422WithNoFieldNamed() throws Exception {
        mvc.perform(get("/api/check-violation-unnamed"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void aUniqueIndexSection9NamesBecomesATyped409() throws Exception {
        mvc.perform(get("/api/unique-violation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/email-taken"))
                .andExpect(jsonPath("$.code").value("user.emailTaken"));
    }

    @Test
    void aUniqueIndexSection9DoesNotNameIsAnUntyped409() throws Exception {
        mvc.perform(get("/api/unique-violation-untyped"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.code").value("common.valueTaken"))
                .andExpect(jsonPath("$.detail").value("That value is already taken."));
    }

    @Test
    void aUniqueViolationWithNoConstraintNameIsAnUntyped409() throws Exception {
        mvc.perform(get("/api/unique-violation-unnamed"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("common.valueTaken"));
    }

    @Test
    void anyOtherIntegrityViolationIsAGeneric409() throws Exception {
        mvc.perform(get("/api/foreign-key-violation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("common.conflict"))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void beanValidationIs422AndNot400() throws Exception {
        mvc.perform(post("/api/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"a\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.instance").value("/api/validated"));
    }

    @Test
    void aBrokenConstraintServesItsNameAsTheCodeAndItsMessageAsTheDetail() throws Exception {
        mvc.perform(post("/api/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"a\",\"email\":\"nije adresa\"}"))
                .andExpect(jsonPath("$.errors.username.code").value("validation.size"))
                .andExpect(jsonPath("$.errors.username.detail").value("Between 3 and 30 characters."))
                .andExpect(jsonPath("$.errors.email.code").value("validation.email"))
                .andExpect(jsonPath("$.errors.email.detail").value("That is not a valid email address."));
    }

    @Test
    void aSizeConstraintServesItsBoundsByName() throws Exception {
        mvc.perform(post("/api/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"a\"}"))
                .andExpect(jsonPath("$.errors.username.args.min").value(3))
                .andExpect(jsonPath("$.errors.username.args.max").value(30));
    }

    @Test
    void aConstraintWithNoNumericAttributeServesNoArgs() throws Exception {
        mvc.perform(post("/api/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"   \",\"email\":\"nije adresa\"}"))
                .andExpect(jsonPath("$.errors.username.code").value("validation.notBlank"))
                .andExpect(jsonPath("$.errors.username.args").doesNotExist())
                .andExpect(jsonPath("$.errors.email.args").doesNotExist());
    }

    @Test
    void anUnmappedMethodIsAProblemDetailToo() throws Exception {
        mvc.perform(post("/api/not-found")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
