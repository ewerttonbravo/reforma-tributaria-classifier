package com.ebravo.taxclassifier.adapter.calculator;

import java.time.LocalDate;
import java.util.function.Function;

/**
 * Holds the value read for the last date asked; another date replaces it. A read that throws keeps nothing.
 * Two threads missing at once both read, which repeats a request but never gives a wrong value.
 */
final class LatestDateCache<T> {

    private record Entry<T>(LocalDate date, T value) {}

    private final Function<LocalDate, T> read;
    private volatile Entry<T> latest;

    LatestDateCache(Function<LocalDate, T> read) {
        this.read = read;
    }

    T on(LocalDate date) {
        var entry = latest;
        if (entry == null || !entry.date().equals(date)) {
            entry = new Entry<>(date, read.apply(date));
            latest = entry;
        }
        return entry.value();
    }
}
