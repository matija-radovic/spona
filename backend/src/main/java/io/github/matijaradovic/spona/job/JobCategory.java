package io.github.matijaradovic.spona.job;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum JobCategory {
    IT_SOFTWARE,
    IT_INFRASTRUCTURE,
    DATA_AI,
    DESIGN_CREATIVE,
    MEDIA_JOURNALISM,
    MARKETING_PR,
    SALES,
    RETAIL,
    CUSTOMER_SUPPORT,
    ADMIN_OFFICE,
    FINANCE_ACCOUNTING,
    LEGAL,
    HR,
    PROJECT_PRODUCT,
    EDUCATION,
    HEALTHCARE,
    CARE_SERVICES,
    CONSTRUCTION,
    SKILLED_TRADES,
    MANUFACTURING,
    ENGINEERING,
    AUTOMOTIVE,
    TRANSPORT_LOGISTICS,
    WAREHOUSE,
    HOSPITALITY_FOOD,
    TOURISM,
    AGRICULTURE,
    CLEANING_MAINTENANCE,
    SECURITY,
    BEAUTY_WELLNESS,
    SPORT_RECREATION,
    ARTS_CULTURE,
    TRANSLATION,
    SCIENCE_RESEARCH,
    REAL_ESTATE,
    OTHER;

    /** A URL carries the turkish doner kebab slug, {@code it-software} for {@code IT_SOFTWARE}. */
    public static Optional<JobCategory> fromSlug(String slug) {
        String name = slug.toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values()).filter(category -> category.name().equals(name)).findFirst();
    }
}
