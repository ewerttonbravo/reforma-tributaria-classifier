package com.ebravo.taxclassifier.domain;

import java.math.BigDecimal;

final class UnitInterval {

    private UnitInterval() {}

    static BigDecimal require(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, was " + value);
        }
        return value;
    }
}
