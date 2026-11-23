package com.ebravo.taxclassifier.domain;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SuggestionTest {

    @Test
    void confidenceMustBeGiven() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suggestion("200", "200003", null))
                .withMessage("confidence must be between 0 and 1, was null");
    }

    @Test
    void confidenceBelowZeroIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suggestion("200", "200003", new BigDecimal("-0.01")))
                .withMessage("confidence must be between 0 and 1, was -0.01");
    }

    @Test
    void confidenceAboveOneIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suggestion("200", "200003", new BigDecimal("1.01")))
                .withMessage("confidence must be between 0 and 1, was 1.01");
    }

    @Test
    void confidenceMayBeZeroOrOne() {
        assertThatNoException().isThrownBy(() -> new Suggestion("200", "200003", BigDecimal.ZERO));
        assertThatNoException().isThrownBy(() -> new Suggestion("200", "200003", BigDecimal.ONE));
    }
}
