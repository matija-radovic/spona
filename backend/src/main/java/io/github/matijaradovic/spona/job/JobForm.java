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
        Map<String, String> errors = new LinkedHashMap<>();

        String title = Strings.trimToEmpty(fields.title());
        blankOrBetween(errors, "title", title, TITLE_MIN, TITLE_MAX);
        String description = Strings.trimToEmpty(fields.description());
        blankOrBetween(errors, "description", description, DESCRIPTION_MIN, DESCRIPTION_MAX);
        String location = Strings.trimToEmpty(fields.location());
        if (length(location) > LOCATION_MAX) {
            errors.put("location", "At most " + LOCATION_MAX + " characters.");
        }

        List<String> keywords = fields.keywords() == null
                ? List.of()
                : fields.keywords().stream().map(keyword -> Strings.trimToEmpty(keyword)).toList();
        if (keywords.size() > KEYWORDS_MAX) {
            errors.put("keywords", "At most " + KEYWORDS_MAX + " keywords.");
        } else if (keywords.stream().anyMatch(keyword -> keyword.isEmpty() || length(keyword) > KEYWORD_MAX)) {
            errors.put("keywords", "Each keyword is 1 to " + KEYWORD_MAX + " characters.");
        }

        SalaryRequest salary = fields.salary();
        if (salary != null && salary.min() != null && salary.max() != null && salary.max().compareTo(salary.min()) < 0) {
            errors.put("salary.max", "Must not be below the minimum.");
        }

        ApplicationType type = fields.applicationType();
        List<ContactEntry> contacts = type != ApplicationType.CONTACT_ONLY
                ? null
                : fields.contactInformation() == null ? List.of() : fields.contactInformation();
        String externalUrl = type == ApplicationType.EXTERNAL_URL ? Strings.trimToEmpty(fields.externalUrl()) : null;
        if (externalUrl != null && length(externalUrl) > EXTERNAL_URL_MAX) {
            errors.put("externalUrl", "At most " + EXTERNAL_URL_MAX + " characters.");
        } else if (externalUrl != null && !externalUrl.isEmpty() && !isWebAddress(externalUrl)) {
            errors.put("externalUrl", "An http or https address is required.");
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
        Map<String, String> missing = new LinkedHashMap<>();
        if (job.getTitle().isBlank()) {
            missing.put("title", "A title is required to publish.");
        }
        if (job.getDescription().isBlank()) {
            missing.put("description", "A description is required to publish.");
        }
        if (job.getCategory() == null) {
            missing.put("category", "A category is required to publish.");
        }
        if (job.getJobType() == null) {
            missing.put("jobType", "A job type is required to publish.");
        }
        if (job.getWorkplaceType() == null) {
            missing.put("workplaceType", "A workplace type is required to publish.");
        }
        if (job.getLocation().isBlank()) {
            missing.put("location", "A location is required to publish.");
        }
        if (job.getApplicationType() == ApplicationType.CONTACT_ONLY
                && (job.getContactInformation() == null || job.getContactInformation().isEmpty())) {
            missing.put("contactInformation", "At least one contact is required to publish.");
        }
        if (job.getApplicationType() == ApplicationType.EXTERNAL_URL
                && (job.getExternalUrl() == null || job.getExternalUrl().isBlank())) {
            missing.put("externalUrl", "An address to apply at is required to publish.");
        }
        if (!missing.isEmpty()) {
            throw new UnprocessableException("The posting is not complete enough to publish.", missing);
        }
    }

    private static void blankOrBetween(Map<String, String> errors, String field, String value, int min, int max) {
        int length = length(value);
        if (length > 0 && length < min) {
            errors.put(field, "At least " + min + " characters.");
        } else if (length > max) {
            errors.put(field, "At most " + max + " characters.");
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
