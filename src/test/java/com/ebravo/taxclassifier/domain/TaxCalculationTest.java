package com.ebravo.taxclassifier.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebravo.taxclassifier.domain.TaxCalculation.Calculated;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** An amount missing from the calculator's answer must fail, never reach a classification as null. */
class TaxCalculationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("0.23");

    @Test
    void calculationWithoutBaseIsRejected() {
        assertThatThrownBy(() -> new Calculated(null, AMOUNT, AMOUNT, false)).hasMessage("base");
    }

    @Test
    void calculationWithoutCbsIsRejected() {
        assertThatThrownBy(() -> new Calculated(AMOUNT, null, AMOUNT, false)).hasMessage("cbs");
    }

    @Test
    void calculationWithoutIbsIsRejected() {
        assertThatThrownBy(() -> new Calculated(AMOUNT, AMOUNT, null, false)).hasMessage("ibs");
    }
}
