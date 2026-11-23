package com.ebravo.taxclassifier.domain;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.ebravo.taxclassifier.domain.Verdict.NeedsReview;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VerdictTest {

    @Test
    void reviewAlwaysCarriesAReason() {
        var suggestion = new Suggestion("200", "200003", BigDecimal.ONE, "reason");

        assertThatNullPointerException()
                .isThrownBy(() -> new NeedsReview(suggestion, null))
                .withMessage("reason");
    }
}
