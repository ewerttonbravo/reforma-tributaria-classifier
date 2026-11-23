package com.ebravo.taxclassifier.domain;

import java.math.BigDecimal;

/** The CST and cClassTrib the AI chose for a product, with the confidence it declared. */
public record Suggestion(String cst, String cClassTrib, BigDecimal confidence) {}
