package io.github.matijaradovic.spona.job;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.matijaradovic.spona.common.dto.ContactEntry;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.Strings;
import io.github.matijaradovic.spona.job.dto.JobFields;
import io.github.matijaradovic.spona.job.dto.SalaryRequest;

record JobForm(
        String title,
        String description,
        List<String> keywords,
        JobCategory category,
        JobType jobType,
        WorkplaceType workplaceType,
        WorkingHours workingHours,
        ExperienceLevel experienceLevel,
        String location,
        SalaryRequest salary,
        ApplicationType applicationType,
        List<ContactEntry> contactInformation,
        String externalUrl) {

    static final int TITLE_MIN = 3;
    static final int TITLE_MAX = 120;
    static final int DESCRIPTION_MIN = 40;
    static final int DESCRIPTION_MAX = 5000;
    static final int LOCATION_MAX = 120;
    static final int EXTERNAL_URL_MAX = 300;
    static final int KEYWORDS_MAX = 10;
    static final int KEYWORD_MAX = 32;

    static JobForm of(JobFields fields) {
        Map<String, ErrorCode.Formatted> errors = new LinkedHashMap<>();

        String title = Strings.trimToEmpty(fields.title());
        blankOrBetween(errors, "title", title, TITLE_MIN, TITLE_MAX);
        String description = Strings.trimToEmpty(fields.description());
        blankOrBetween(errors, "description", description, DESCRIPTION_MIN, DESCRIPTION_MAX);
        String location = Strings.trimToEmpty(fields.location());
        if (length(location) > LOCATION_MAX) {
            errors.put("location", ErrorCode.JOB_LOCATION_TOO_LONG.with(LOCATION_MAX));
        }

        List<String> keywords = fields.keywords() == null
                ? List.of()
                : fields.keywords().stream().map(keyword -> Strings.trimToEmpty(keyword)).toList();
        if (keywords.size() > KEYWORDS_MAX) {
            errors.put("keywords", ErrorCode.JOB_KEYWORDS_TOO_MANY.with(KEYWORDS_MAX));
        } else if (keywords.stream().anyMatch(keyword -> keyword.isEmpty() || length(keyword) > KEYWORD_MAX)) {
            errors.put("keywords", ErrorCode.JOB_KEYWORD_LENGTH.with(KEYWORD_MAX));
        }

        SalaryRequest salary = fields.salary();
        if (salary != null && salary.min() != null && salary.max() != null && salary.max().compareTo(salary.min()) < 0) {
            errors.put("salary.max", ErrorCode.JOB_SALARY_MAX_BELOW_MIN.with());
        }

        ApplicationType type = fields.applicationType();
        List<ContactEntry> contacts = type != ApplicationType.CONTACT_ONLY
                ? null
                : fields.contactInformation() == null ? List.of() : fields.contactInformation();
        String externalUrl = type == ApplicationType.EXTERNAL_URL ? Strings.trimToEmpty(fields.externalUrl()) : null;
        if (externalUrl != null && length(externalUrl) > EXTERNAL_URL_MAX) {
            errors.put("externalUrl", ErrorCode.JOB_EXTERNAL_URL_TOO_LONG.with(EXTERNAL_URL_MAX));
        } else if (externalUrl != null && !externalUrl.isEmpty() && !isWebAddress(externalUrl)) {
            errors.put("externalUrl", ErrorCode.JOB_EXTERNAL_URL_NOT_WEB.with());
        }

        if (!errors.isEmpty()) {
            throw new UnprocessableException(errors.values().iterator().next(), errors);
        }
        return new JobForm(title, description, keywords, fields.category(), fields.jobType(), fields.workplaceType(),
                fields.workingHours(), fields.experienceLevel(), location, salary, type, contacts, externalUrl);
    }

    void applyTo(Job job) {
        job.setTitle(title);
        job.setDescription(description);
        job.setKeywords(new ArrayList<>(keywords));
        job.setCategory(category);
        job.setJobType(jobType);
        job.setWorkplaceType(workplaceType);
        job.setWorkingHours(workingHours);
        job.setExperienceLevel(experienceLevel);
        job.setLocation(location);
        job.setApplicationType(applicationType);
        job.setContactInformation(contactInformation == null ? null : new ArrayList<>(contactInformation));
        job.setExternalUrl(externalUrl);
        applySalary(job);
    }

    private void applySalary(Job job) {
        Salary held = job.getSalary();
        if (salary == null) {
            job.setSalary(null);
        } else if (held == null) {
            job.setSalary(new Salary(job, amount(salary.min()), amount(salary.max()), salary.currency(),
                    salary.period(), null));
        } else {
            held.setMin(amount(salary.min()));
            held.setMax(amount(salary.max()));
            held.setCurrency(salary.currency());
            held.setPeriod(salary.period());
        }
    }

    static void checkPublishable(Job job) {
        Map<String, ErrorCode.Formatted> missing = new LinkedHashMap<>();
        if (job.getTitle().isBlank()) {
            missing.put("title", ErrorCode.JOB_TITLE_REQUIRED.with());
        }
        if (job.getDescription().isBlank()) {
            missing.put("description", ErrorCode.JOB_DESCRIPTION_REQUIRED.with());
        }
        if (job.getCategory() == null) {
            missing.put("category", ErrorCode.JOB_CATEGORY_REQUIRED.with());
        }
        if (job.getJobType() == null) {
            missing.put("jobType", ErrorCode.JOB_TYPE_REQUIRED.with());
        }
        if (job.getWorkplaceType() == null) {
            missing.put("workplaceType", ErrorCode.JOB_WORKPLACE_TYPE_REQUIRED.with());
        }
        if (job.getLocation().isBlank()) {
            missing.put("location", ErrorCode.JOB_LOCATION_REQUIRED.with());
        }
        if (job.getApplicationType() == ApplicationType.CONTACT_ONLY
                && (job.getContactInformation() == null || job.getContactInformation().isEmpty())) {
            missing.put("contactInformation", ErrorCode.JOB_CONTACT_REQUIRED.with());
        }
        if (job.getApplicationType() == ApplicationType.EXTERNAL_URL
                && (job.getExternalUrl() == null || job.getExternalUrl().isBlank())) {
            missing.put("externalUrl", ErrorCode.JOB_EXTERNAL_URL_REQUIRED.with());
        }
        if (!missing.isEmpty()) {
            throw new UnprocessableException(ErrorCode.JOB_INCOMPLETE.with(), missing);
        }
    }

    private static void blankOrBetween(Map<String, ErrorCode.Formatted> errors, String field, String value, int min,
            int max) {
        int length = length(value);
        if ((length > 0 && length < min) || length > max) {
            errors.put(field, ErrorCode.VALIDATION_SIZE.with(min, max));
        }
    }

    /** Code points, as {@code char_length} counts them. */
    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isWebAddress(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getRawAuthority() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
