package io.github.matijaradovic.spona.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class UnprocessableException extends ApiException {

    private final Map<String, ErrorCode.Formatted> errors;

    public UnprocessableException(ErrorCode code) {
        this(code.with(), Map.of());
    }

    public UnprocessableException(ErrorCode.Formatted error, Map<String, ErrorCode.Formatted> errors) {
        super(error);
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public static UnprocessableException on(String field, ErrorCode code) {
        return on(field, code.with());
    }

    public static UnprocessableException on(String field, ErrorCode.Formatted error) {
        return new UnprocessableException(error, Map.of(field, error));
    }

    public Map<String, ErrorCode.Formatted> getErrors() {
        return errors;
    }
}
