package io.github.matijaradovic.spona.common.web;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.matijaradovic.spona.common.error.ErrorCode;

public record ApiFieldError(
        String code,
        String detail,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> args) {

    static ApiFieldError of(ErrorCode.Formatted error) {
        return new ApiFieldError(error.errorCode().code(), error.detail(),
                error.args().isEmpty() ? null : error.args());
    }
}
