package com.ebravo.taxclassifier.domain;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.ebravo.taxclassifier.domain.Classification.Classified;
import com.ebravo.taxclassifier.domain.Classification.ToReview;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ClassificationTest {

    private static final Suggestion SUGGESTION = new Suggestion("200", "200003", BigDecimal.ONE, "reason");

    @Test
    void reviewAlwaysCarriesAReason() {
        assertThatNullPointerException().isThrownBy(() -> new ToReview(SUGGESTION, null)).withMessage("reason");
    }

    @Test
    void missingLegalBasisIsEmptyNeverNull() {
        assertThatNullPointerException().isThrownBy(() -> new Classified(SUGGESTION, null)).withMessage("legalBasis");
    }
}
