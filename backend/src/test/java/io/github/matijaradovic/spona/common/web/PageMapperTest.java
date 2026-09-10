package io.github.matijaradovic.spona.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PageMapperTest {

    private MockHttpServletRequest request;

    @BeforeEach
    void bindRequest() {
        request = new MockHttpServletRequest("GET", "/api/jobs");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void translatesTheWiresFirstPageToPageableZero() {
        assertThat(PageMapper.toPageable(1, 20).getPageNumber()).isZero();
        assertThat(PageMapper.toPageable(3, 20).getPageNumber()).isEqualTo(2);
    }

    @Test
    void clampsAPageBelowOneAndASizeAboveTheCeiling() {
        assertThat(PageMapper.toPageable(0, 20).getPageNumber()).isZero();
        assertThat(PageMapper.toPageable(1, 0).getPageSize()).isEqualTo(1);
        assertThat(PageMapper.toPageable(1, 5000).getPageSize()).isEqualTo(PageMapper.MAX_SIZE);
    }

    @Test
    void translatesPageableZeroBackToTheWiresFirstPage() {
        Pageable pageable = PageRequest.of(0, 10);
        PagedResponse<String> response = PageMapper.toResponse(new PageImpl<>(List.of("a", "b"), pageable, 25));

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.last()).isFalse();
        assertThat(response.links().self().href()).isEqualTo("/api/jobs?page=1&size=10");
        assertThat(response.links().next().href()).isEqualTo("/api/jobs?page=2&size=10");
    }

    @Test
    void carriesTheRequestsOwnQueryStringIntoEveryHref() {
        request.setParameter("page", "2");
        request.setParameter("size", "10");
        request.setParameter("category", "TRANSPORT_LOGISTICS");

        PagedResponse<String> response = PageMapper.toResponse(
                new PageImpl<>(List.of("a"), PageRequest.of(1, 10), 25));

        assertThat(response.links().prev().href())
                .isEqualTo("/api/jobs?page=1&size=10&category=TRANSPORT_LOGISTICS");
        assertThat(response.links().next().href())
                .isEqualTo("/api/jobs?page=3&size=10&category=TRANSPORT_LOGISTICS");
    }

    @Test
    void anEmptyResultIsTheLastPage() {
        PagedResponse<String> response = PageMapper.toResponse(
                new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalPages()).isZero();
        assertThat(response.last()).isTrue();
        assertThat(response.content()).isEmpty();
    }

    @Test
    void mapsTheRowsWhileTranslatingThePage() {
        PagedResponse<Integer> response = PageMapper.toResponse(
                new PageImpl<>(List.of("a", "bb"), PageRequest.of(1, 2), 6), row -> row.length());

        assertThat(response.content()).containsExactly(1, 2);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(6);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
