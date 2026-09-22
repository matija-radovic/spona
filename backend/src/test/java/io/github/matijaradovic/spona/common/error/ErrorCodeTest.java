package io.github.matijaradovic.spona.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import io.github.matijaradovic.spona.common.web.ProblemTypes;
import io.github.matijaradovic.spona.job.JobStatus;
import jakarta.validation.Constraint;

class ErrorCodeTest {

    private static final Pattern TWO_OR_MORE_SEGMENTS = Pattern.compile("[a-z][a-zA-Z0-9]*(\\.[a-z][a-zA-Z0-9]*)+");
    private static final Pattern FORMAT_SPECIFIER = Pattern.compile("%[ds]");

    @Test
    void everyCodeIsUniqueAndNotBlank() {
        List<String> codes = Arrays.stream(ErrorCode.values()).map(value -> value.code()).toList();

        assertThat(codes).doesNotHaveDuplicates().allSatisfy(code -> assertThat(code).isNotBlank());
    }

    @Test
    void everyCodeIsADomainAndAReasonInCamelCase() {
        assertThat(ErrorCode.values()).allSatisfy(
                value -> assertThat(value.code()).matches(TWO_OR_MORE_SEGMENTS));
    }

    @Test
    void everyEnglishHasOneFormatSpecifierPerArgumentName() {
        assertThat(ErrorCode.values()).allSatisfy(value -> assertThat(
                FORMAT_SPECIFIER.matcher(value.detail()).results().count())
                .as(value.name())
                .isEqualTo(value.arity()));
    }

    @Test
    void everyProblemTypeIsServedBySomeCode() throws IllegalAccessException {
        Set<URI> served = Arrays.stream(ErrorCode.values())
                .map(value -> value.type())
                .filter(type -> type != null)
                .collect(Collectors.toSet());

        for (Field field : ProblemTypes.class.getFields()) {
            assertThat(served).contains((URI) field.get(null));
        }
    }

    @Test
    void withFormatsTheEnglishAndServesEachValueUnderItsName() {
        ErrorCode.Formatted error = ErrorCode.JOB_BAD_STATUS_TRANSITION.with(JobStatus.CLOSED, JobStatus.DRAFT);

        assertThat(error.errorCode()).isEqualTo(ErrorCode.JOB_BAD_STATUS_TRANSITION);
        assertThat(error.detail()).isEqualTo("A posting cannot move from CLOSED to DRAFT.");
        assertThat(error.args()).containsExactly(entry("from", "CLOSED"), entry("to", "DRAFT"));
    }

    @Test
    void aNumberStaysANumberInTheArguments() {
        ErrorCode.Formatted error = ErrorCode.POST_CONTENT_TOO_LONG.with(5000);

        assertThat(error.detail()).isEqualTo("At most 5000 characters.");
        assertThat(error.args()).containsExactly(entry("max", 5000));
    }

    @Test
    void aCodeWithNoArgumentsFormatsToItsEnglishAndNoArguments() {
        ErrorCode.Formatted error = ErrorCode.ACTOR_NOT_FOUND.with();

        assertThat(error.detail()).isEqualTo("Actor not found.");
        assertThat(error.args()).isEmpty();
    }

    @Test
    void withRefusesTheWrongNumberOfArguments() {
        assertThatThrownBy(() -> ErrorCode.JOB_BAD_STATUS_TRANSITION.with(JobStatus.OPEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ErrorCode.ACTOR_NOT_FOUND.with("extra"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCodeThatTakesArgumentsCannotBeThrownWithoutThem() {
        assertThatThrownBy(() -> new ConflictException(ErrorCode.JOB_BAD_STATUS_TRANSITION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anExceptionAnswersItsEnglishAsItsMessage() {
        ConflictException thrown = new ConflictException(ErrorCode.USERNAME_TAKEN);

        assertThat(thrown).hasMessage("That username is taken.");
        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.USERNAME_TAKEN);
    }

    @Test
    void aConstraintNameResolvesToItsValidationCode() {
        assertThat(ErrorCode.forConstraint("NotBlank")).isEqualTo(ErrorCode.VALIDATION_NOT_BLANK);
        assertThat(ErrorCode.forConstraint("Size")).isEqualTo(ErrorCode.VALIDATION_SIZE);
        assertThat(ErrorCode.forConstraint("PositiveOrZero")).isEqualTo(ErrorCode.VALIDATION_POSITIVE_OR_ZERO);
        assertThat(ErrorCode.forConstraint("DecimalMax")).isEqualTo(ErrorCode.VALIDATION_DECIMAL_MAX);
    }

    @Test
    void aConstraintTheCatalogLacksIsAnInvalidValue() {
        assertThat(ErrorCode.forConstraint("Min")).isEqualTo(ErrorCode.VALIDATION_INVALID);
        assertThat(ErrorCode.forConstraint("typeMismatch")).isEqualTo(ErrorCode.VALIDATION_INVALID);
        assertThat(ErrorCode.forConstraint(null)).isEqualTo(ErrorCode.VALIDATION_INVALID);
        assertThat(ErrorCode.forConstraint("")).isEqualTo(ErrorCode.VALIDATION_INVALID);
    }

    @Test
    void everyConstraintTheApplicationDeclaresHasACodeOfItsOwn() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return true;
            }
        };
        scanner.addIncludeFilter((reader, factory) -> true);

        Set<String> constraints = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("io.github.matijaradovic.spona")) {
            Class<?> type = Class.forName(candidate.getBeanClassName(), false, getClass().getClassLoader());
            if (type.getProtectionDomain().getCodeSource().getLocation().getPath().contains("test-classes")) {
                continue;
            }
            for (Field field : type.getDeclaredFields()) {
                for (Annotation annotation : field.getAnnotations()) {
                    if (annotation.annotationType().isAnnotationPresent(Constraint.class)) {
                        constraints.add(annotation.annotationType().getSimpleName());
                    }
                }
            }
        }

        assertThat(constraints).contains("NotBlank", "Size").allSatisfy(
                name -> assertThat(ErrorCode.forConstraint(name)).as(name).isNotEqualTo(ErrorCode.VALIDATION_INVALID));
    }
}
