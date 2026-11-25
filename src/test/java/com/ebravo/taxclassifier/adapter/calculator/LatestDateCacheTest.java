package com.ebravo.taxclassifier.adapter.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LatestDateCacheTest {

    private static final LocalDate MONDAY = LocalDate.of(2027, 3, 8);
    private static final LocalDate TUESDAY = LocalDate.of(2027, 3, 9);

    private final List<LocalDate> reads = new ArrayList<>();
    private final LatestDateCache<String> cache = new LatestDateCache<>(date -> {
        reads.add(date);
        return "list of " + date;
    });

    @Test
    void readsOncePerDate() {
        assertThat(cache.on(MONDAY)).isEqualTo("list of 2027-03-08");
        assertThat(cache.on(MONDAY)).isEqualTo("list of 2027-03-08");

        assertThat(reads).containsExactly(MONDAY);
    }

    /** A cache of every date would read Monday once; only the last date read is kept. */
    @Test
    void keepsOnlyTheLastDateRead() {
        cache.on(MONDAY);
        assertThat(cache.on(TUESDAY)).isEqualTo("list of 2027-03-09");
        assertThat(cache.on(MONDAY)).isEqualTo("list of 2027-03-08");

        assertThat(reads).containsExactly(MONDAY, TUESDAY, MONDAY);
    }
}
