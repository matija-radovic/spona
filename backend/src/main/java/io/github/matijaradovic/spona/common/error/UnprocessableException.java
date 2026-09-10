package io.github.matijaradovic.spona.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class UnprocessableException extends RuntimeException {

    private final Map<String, String> errors;

    public UnprocessableException(String detail) {
        this(detail, Map.of());
    }

    public UnprocessableException(String detail, Map<String, String> errors) {
        super(detail);
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public static UnprocessableException on(String field, String message) {
        return new UnprocessableException(message, Map.of(field, message));
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
