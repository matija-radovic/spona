package io.github.matijaradovic.spona.common.web;

import java.net.URI;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.matijaradovic.spona.common.error.BadRequestException;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.ForbiddenException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnauthorizedException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";

    private static final Map<String, String> CHECK_FIELDS = Map.ofEntries(
            Map.entry("actor_type_check", "actorType"),
            Map.entry("user_username_check", "username"),
            Map.entry("user_full_name_length_check", "fullName"),
            Map.entry("user_email_length_check", "email"),
            Map.entry("user_bio_length_check", "bio"),
            Map.entry("user_location_length_check", "location"),
            Map.entry("user_contact_info_check", "otherContactInfo"),
            Map.entry("org_slug_check", "slug"),
            Map.entry("org_display_name_length_check", "displayName"),
            Map.entry("org_legal_name_length_check", "legalName"),
            Map.entry("org_description_length_check", "description"),
            Map.entry("org_website_length_check", "website"),
            Map.entry("org_location_length_check", "location"),
            Map.entry("org_contact_info_check", "otherContactInfo"),
            Map.entry("follows_no_self_check", "followeeId"),
            Map.entry("post_content_length_check", "content"),
            Map.entry("comment_content_length_check", "content"),
            Map.entry("reaction_emoji_length_check", "emoji"),
            Map.entry("job_keywords_check", "keywords"),
            Map.entry("job_contact_info_check", "contactInformation"),
            Map.entry("job_contact_pairing_check", "contactInformation"),
            Map.entry("job_external_url_pairing_check", "externalUrl"),
            Map.entry("salary_min_check", "salary.min"),
            Map.entry("salary_max_check", "salary.max"),
            Map.entry("salary_currency_check", "salary.currency"),
            Map.entry("salary_display_text_length_check", "salary.displayText"),
            Map.entry("invitation_role_check", "role"),
            Map.entry("invitation_email_length_check", "identifier"),
            Map.entry("invitation_target_check", "identifier"),
            Map.entry("verification_email_length_check", "email"));

    private static final Map<String, ErrorCode> UNIQUE_CODES = Map.of(
            "user_username_key", ErrorCode.USERNAME_TAKEN,
            "user_email_key", ErrorCode.EMAIL_TAKEN,
            "organization_slug_key", ErrorCode.SLUG_TAKEN);

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail onBadRequest(BadRequestException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ex.getError(), request);
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail onNotFound(NotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ex.getError(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail onUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getError(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail onForbidden(ForbiddenException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, ex.getError(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail onConflict(ConflictException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ex.getError(), request);
    }

    @ExceptionHandler(UnprocessableException.class)
    ProblemDetail onUnprocessable(UnprocessableException ex, HttpServletRequest request) {
        ProblemDetail body = problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getError(), request);
        if (!ex.getErrors().isEmpty()) {
            Map<String, ApiFieldError> errors = new LinkedHashMap<>();
            ex.getErrors().forEach((field, error) -> errors.put(field, ApiFieldError.of(error)));
            body.setProperty("errors", errors);
        }
        return body;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail onDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        String constraint = constraintName(ex);
        String sqlState = sqlState(ex);
        logger.warn("Integrity violation on %s: sqlstate=%s constraint=%s"
                .formatted(request.getRequestURI(), sqlState, constraint));

        if (CHECK_VIOLATION.equals(sqlState)) {
            ProblemDetail body = problem(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.VALIDATION_FAILED.with(), request);
            String field = constraint == null ? null : CHECK_FIELDS.get(constraint);
            if (field != null) {
                body.setProperty("errors", Map.of(field, ApiFieldError.of(ErrorCode.VALIDATION_INVALID.with())));
            }
            return body;
        }
        if (UNIQUE_VIOLATION.equals(sqlState)) {
            ErrorCode code = constraint == null ? null : UNIQUE_CODES.get(constraint);
            return problem(HttpStatus.CONFLICT, (code != null ? code : ErrorCode.VALUE_TAKEN).with(), request);
        }
        return problem(HttpStatus.CONFLICT, ErrorCode.CONFLICT.with(), request);
    }

    /** A broken bound on a request body is `422` here, where Spring's own default is `400`. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, ApiFieldError> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), fieldError(error)));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), fieldError(error)));

        ErrorCode failed = ErrorCode.VALIDATION_FAILED;
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, failed.detail());
        body.setTitle(HttpStatus.UNPROCESSABLE_CONTENT.getReasonPhrase());
        body.setProperty("code", failed.code());
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, HttpStatus.UNPROCESSABLE_CONTENT, request);
    }

    static ProblemDetail problem(HttpStatus status, ErrorCode.Formatted error, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, error.detail());
        body.setTitle(status.getReasonPhrase());
        URI type = error.errorCode().type();
        if (type != null) {
            body.setType(type);
        }
        body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty("code", error.errorCode().code());
        if (!error.args().isEmpty()) {
            body.setProperty("args", error.args());
        }
        return body;
    }

    private static ApiFieldError fieldError(ObjectError error) {
        ErrorCode code = ErrorCode.forConstraint(error.getCode());
        String detail = error.getDefaultMessage() != null ? error.getDefaultMessage() : code.detail();
        return new ApiFieldError(code.code(), detail, constraintArguments(error));
    }

    /** The constraint's numeric attributes, such as `min` and `max`, read by name */
    private static Map<String, Object> constraintArguments(ObjectError error) {
        if (!error.contains(ConstraintViolation.class)) {
            return null;
        }
        ConstraintViolation<?> violation = error.unwrap(ConstraintViolation.class);
        Map<String, Object> args = new LinkedHashMap<>();
        violation.getConstraintDescriptor().getAttributes().forEach((name, value) -> {
            if (value instanceof Number) {
                args.put(name, value);
            }
        });
        return args.isEmpty() ? null : args;
    }

    private static String constraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

    private static String sqlState(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
