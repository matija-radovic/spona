package io.github.matijaradovic.spona.job;

public enum JobStatus {
    DRAFT,
    OPEN,
    CLOSED;

    /** DRAFT to OPEN, and OPEN and CLOSED both ways, nothing returns to DRAFT */
    public boolean canMoveTo(JobStatus next) {
        return switch (this) {
            case DRAFT, CLOSED -> next == OPEN;
            case OPEN -> next == CLOSED;
        };
    }
}
