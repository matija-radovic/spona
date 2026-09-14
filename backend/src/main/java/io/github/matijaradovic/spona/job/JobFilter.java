package io.github.matijaradovic.spona.job;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

record JobFilter(String q, JobCategory category, Posted posted, Sort sort, Set<WorkplaceType> workplace,
        Set<JobType> employment, Set<WorkingHours> hours, Set<ExperienceLevel> experience, boolean salary) {

    enum Posted {
        DAY("24h", "24 hours"),
        WEEK("week", "7 days"),
        MONTH("month", "30 days");

        private final String value;
        private final String window;

        Posted(String value, String window) {
            this.value = value;
            this.window = window;
        }

        String window() {
            return window;
        }

        static Optional<Posted> fromValue(String value) {
            return Arrays.stream(values()).filter(posted -> posted.value.equals(value)).findFirst();
        }
    }

    enum Sort {
        RELEVANCE(null),
        DATE_DESC("date_desc"),
        DATE_ASC("date_asc");

        private final String value;

        Sort(String value) {
            this.value = value;
        }

        static Optional<Sort> fromValue(String value) {
            return Arrays.stream(values()).filter(sort -> value.equals(sort.value)).findFirst();
        }
    }
}
