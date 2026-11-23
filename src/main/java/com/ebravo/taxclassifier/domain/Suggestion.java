package com.ebravo.taxclassifier.domain;

import java.math.BigDecimal;

/** The CST and cClassTrib the AI chose for a product, with the confidence it declared, from 0 to 1, and why it chose them. */
public record Suggestion(String cst, String cClassTrib, BigDecimal confidence, String justification) {

    public Suggestion {
        requireText(cst, "cst");
        requireText(cClassTrib, "cClassTrib");
        UnitInterval.require(confidence, "confidence");
        requireText(justification, "justification");
    }

    private static void requireText(String text, String name) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank, was " + (text == null ? null : "'" + text + "'"));
        }
    }
}
