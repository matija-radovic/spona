package io.github.matijaradovic.spona.common.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PagedResponse<T>(
        @JsonProperty("_links") Links links,
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public record HalLink(String href) {
    }

    /** `prev` is absent on the first page, `next` on the last. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Links(HalLink self, HalLink prev, HalLink next) {
    }
}
