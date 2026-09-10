package io.github.matijaradovic.spona.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.matijaradovic.spona.common.web.PagedResponse.Links;

class HalLinkBuilderTest {

    private static Map<String, String[]> query(String... pairs) {
        Map<String, String[]> query = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            query.put(pairs[i], new String[] { pairs[i + 1] });
        }
        return query;
    }

    @Test
    void writesPageThenSizeThenTheRestInRequestOrder() {
        String href = HalLinkBuilder.href("/api/jobs", 2, 20, query("type", "user", "q", "vozac"));

        assertThat(href).isEqualTo("/api/jobs?page=2&size=20&type=user&q=vozac");
    }

    @Test
    void keepsPageAndSizeFirstEvenWhenTheRequestSentThemLast() {
        String href = HalLinkBuilder.href("/api/actors", 1, 10, query("q", "ana", "page", "1", "size", "10"));

        assertThat(href).isEqualTo("/api/actors?page=1&size=10&q=ana");
    }

    @Test
    void encodesNamesAndValues() {
        String href = HalLinkBuilder.href("/api/jobs", 1, 20, query("q", "dostavljač u Beogradu"));

        assertThat(href).isEqualTo("/api/jobs?page=1&size=20&q=dostavlja%C4%8D+u+Beogradu");
    }

    @Test
    void omitsPrevOnTheFirstPageAndNextOnTheLast() {
        Links first = HalLinkBuilder.links("/api/feed", query(), 1, 10, 3);
        assertThat(first.prev()).isNull();
        assertThat(first.next().href()).isEqualTo("/api/feed?page=2&size=10");

        Links last = HalLinkBuilder.links("/api/feed", query(), 3, 10, 3);
        assertThat(last.prev().href()).isEqualTo("/api/feed?page=2&size=10");
        assertThat(last.next()).isNull();
    }

    @Test
    void anEmptyResultCarriesSelfAlone() {
        Links links = HalLinkBuilder.links("/api/feed", query(), 1, 10, 0);

        assertThat(links.self().href()).isEqualTo("/api/feed?page=1&size=10");
        assertThat(links.prev()).isNull();
        assertThat(links.next()).isNull();
    }
}
