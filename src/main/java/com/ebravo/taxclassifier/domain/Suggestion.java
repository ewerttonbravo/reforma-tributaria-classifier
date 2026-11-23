package com.ebravo.taxclassifier.domain;

import java.math.BigDecimal;

/** The CST and cClassTrib the AI chose for a product, with the confidence it declared, from 0 to 1. */
public record Suggestion(String cst, String cClassTrib, BigDecimal confidence) {

    public Suggestion {
        UnitInterval.require(confidence, "confidence");
    }
}
