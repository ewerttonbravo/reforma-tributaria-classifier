package com.ebravo.taxclassifier.domain;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.ebravo.taxclassifier.domain.Classification.Classified;
import com.ebravo.taxclassifier.domain.Classification.ToReview;
import com.ebravo.taxclassifier.domain.TaxCalculation.Calculated;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClassificationTest {

    private static final Suggestion SUGGESTION = new Suggestion("200", "200003", BigDecimal.ONE, "reason");
    private static final Calculated CALCULATION = new Calculated(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, false);

    @Test
    void reviewAlwaysCarriesAReason() {
        assertThatNullPointerException().isThrownBy(() -> new ToReview(SUGGESTION, null)).withMessage("reason");
    }

    @Test
    void missingLegalBasisIsEmptyNeverNull() {
        assertThatNullPointerException().isThrownBy(() -> new Classified(SUGGESTION, null, CALCULATION)).withMessage("legalBasis");
    }

    @Test
    void classificationAlwaysCarriesItsCalculation() {
        assertThatNullPointerException().isThrownBy(() -> new Classified(SUGGESTION, Optional.empty(), null)).withMessage("calculation");
    }
}
