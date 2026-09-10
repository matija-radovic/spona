package io.github.matijaradovic.spona.common.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** Actor discriminant */
public enum ActorType {

    USER("user"),
    ORG("org");

    private final String value;

    ActorType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
