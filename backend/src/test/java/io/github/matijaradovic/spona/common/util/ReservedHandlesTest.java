package io.github.matijaradovic.spona.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReservedHandlesTest {

    @Test
    void reservesEveryKindOfStaticSegment() {
        assertThat(ReservedHandles.isReserved("jobs")).isTrue();
        assertThat(ReservedHandles.isReserved("register")).isTrue();
        assertThat(ReservedHandles.isReserved("settings")).isTrue();
        assertThat(ReservedHandles.isReserved("followers")).isTrue();
        assertThat(ReservedHandles.isReserved("api")).isTrue();
        assertThat(ReservedHandles.isReserved("well-known")).isTrue();
    }

    @Test
    void matchesTheLowercasedHandle() {
        assertThat(ReservedHandles.isReserved("API")).isTrue();
        assertThat(ReservedHandles.isReserved("Admin")).isTrue();
        assertThat(ReservedHandles.isReserved("  verify  ")).isTrue();
    }

    /** A near miss is not a match: only the segment itself is reserved. */
    @Test
    void allowsAnOrdinaryHandle() {
        assertThat(ReservedHandles.isReserved("marko")).isFalse();
        assertThat(ReservedHandles.isReserved("user")).isFalse();
        assertThat(ReservedHandles.isReserved("job")).isFalse();
        assertThat(ReservedHandles.isReserved("spona-tim")).isFalse();
        assertThat(ReservedHandles.isReserved(null)).isFalse();
    }
}
