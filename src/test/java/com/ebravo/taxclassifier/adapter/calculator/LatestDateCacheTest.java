package com.ebravo.taxclassifier.adapter.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** A calculator that fails once must be asked again, not answered from the failure. */
    @Test
    void failedReadIsNeverCached() {
        var attempts = new AtomicInteger();
        var flaky = new LatestDateCache<>(date -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("calculator down");
            }
            return "list of " + date;
        });

        assertThatThrownBy(() -> flaky.on(MONDAY)).hasMessage("calculator down");
        assertThat(flaky.on(MONDAY)).isEqualTo("list of 2027-03-08");
        assertThat(attempts).hasValue(2);
    }

    /** A response with no body reads as null; kept, it would fail every product of the day without a new request. */
    @Test
    void missingValueIsAFailureNeverCached() {
        var attempts = new AtomicInteger();
        var emptyOnce = new LatestDateCache<>(date -> attempts.incrementAndGet() == 1 ? null : "list of " + date);

        assertThatThrownBy(() -> emptyOnce.on(MONDAY))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value read for 2027-03-08");
        assertThat(emptyOnce.on(MONDAY)).isEqualTo("list of 2027-03-08");
        assertThat(attempts).hasValue(2);
    }

    /** A null date kept as the entry would break every later call, whatever its date. */
    @Test
    void nullDateIsRejectedBeforeAnyRead() {
        assertThatThrownBy(() -> cache.on(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date");
        assertThat(reads).isEmpty();
    }
}
