package com.ebravo.taxclassifier.domain;

import java.math.BigDecimal;

/** The CST and cClassTrib the AI chose for a product, with the confidence it declared, from 0 to 1. */
public record Suggestion(String cst, String cClassTrib, BigDecimal confidence) {

    public Suggestion {
        requireCode(cst, "cst");
        requireCode(cClassTrib, "cClassTrib");
        UnitInterval.require(confidence, "confidence");
    }

    private static void requireCode(String code, String name) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank, was " + (code == null ? null : "'" + code + "'"));
        }
    }
}
