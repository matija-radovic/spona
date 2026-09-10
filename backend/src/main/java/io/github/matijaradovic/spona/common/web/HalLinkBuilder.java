package io.github.matijaradovic.spona.common.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.github.matijaradovic.spona.common.web.PagedResponse.HalLink;
import io.github.matijaradovic.spona.common.web.PagedResponse.Links;
import jakarta.servlet.http.HttpServletRequest;

public final class HalLinkBuilder {

    private HalLinkBuilder() {
    }

    /** Every href carries the whole query string, ordered: page, size, then the rest. */
    public static String href(String path, int page, int size, Map<String, String[]> query) {
        StringBuilder href = new StringBuilder(path)
                .append("?page=").append(page)
                .append("&size=").append(size);
        query.forEach((name, values) -> {
            if (name.equals("page") || name.equals("size")) {
                return;
            }
            for (String value : values) {
                href.append('&').append(encode(name)).append('=').append(encode(value));
            }
        });
        return href.toString();
    }

    public static Links links(String path, Map<String, String[]> query, int page, int size, int totalPages) {
        return new Links(
                new HalLink(href(path, page, size, query)),
                page > 1 ? new HalLink(href(path, page - 1, size, query)) : null,
                page < totalPages ? new HalLink(href(path, page + 1, size, query)) : null);
    }

    public static Links forCurrentRequest(int page, int size, int totalPages) {
        HttpServletRequest request = currentRequest();
        return links(request.getRequestURI(), request.getParameterMap(), page, size, totalPages);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new IllegalStateException("HAL links can only be built while a request is being handled");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
