package com.ebravo.taxclassifier.domain;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SuggestionTest {

    @Test
    void cstMustBeGiven() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suggestion(null, "200003", BigDecimal.ONE, "reason"))
                .withMessage("cst must not be blank, was null");
    }

    @Test
    void blankCstIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suggestion("  ", "200003", BigDecimal.ONE, "reason"))
                .withMessage("cst must not be blank, was '  '");
    }

    @Test
    void cClassTribMustBeGiven() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suggestion("200", null, BigDecimal.ONE, "reason"))
                .withMessage("cClassTrib must not be blank, was null");
    }

    @Test
    void justificationMustBeGiven() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suggestion("200", "200003", BigDecimal.ONE, null))
                .withMessage("justification must not be blank, was null");
    }

    @Test
    void blankJustificationIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suggestion("200", "200003", BigDecimal.ONE, " "))
                .withMessage("justification must not be blank, was ' '");
    }

    @Test
    void confidenceMustBeGiven() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suggestion("200", "200003", null, "reason"))
                .withMessage("confidence must be between 0 and 1, was null");
    }

    @Test
    void confidenceBelowZeroIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suggestion("200", "200003", new BigDecimal("-0.01"), "reason"))
                .withMessage("confidence must be between 0 and 1, was -0.01");
    }

    @Test
    void confidenceAboveOneIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suggestion("200", "200003", new BigDecimal("1.01"), "reason"))
                .withMessage("confidence must be between 0 and 1, was 1.01");
    }

    @Test
    void confidenceMayBeZeroOrOne() {
        assertThatNoException().isThrownBy(() -> new Suggestion("200", "200003", BigDecimal.ZERO, "reason"));
        assertThatNoException().isThrownBy(() -> new Suggestion("200", "200003", BigDecimal.ONE, "reason"));
    }
}
