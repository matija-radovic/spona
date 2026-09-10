package io.github.matijaradovic.spona.common.web;

import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** 0 index to 1 index cross */
public final class PageMapper {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageMapper() {
    }

    public static Pageable toPageable(int page, int size) {
        return toPageable(page, size, Sort.unsorted());
    }

    public static Pageable toPageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 1) - 1, Math.clamp(size, 1, MAX_SIZE), sort);
    }

    public static <T> PagedResponse<T> toResponse(Page<T> source) {
        int page = source.getNumber() + 1;
        int totalPages = source.getTotalPages();
        return new PagedResponse<>(
                HalLinkBuilder.forCurrentRequest(page, source.getSize(), totalPages),
                source.getContent(),
                page,
                source.getSize(),
                source.getTotalElements(),
                totalPages,
                page >= totalPages);
    }

    public static <T, R> PagedResponse<R> toResponse(Page<T> source, Function<T, R> mapper) {
        return toResponse(source.map(mapper));
    }
}
