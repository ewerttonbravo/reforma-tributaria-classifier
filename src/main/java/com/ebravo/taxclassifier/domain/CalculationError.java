package com.ebravo.taxclassifier.domain;

/** An error the official calculator reported for an item, such as REG-011 for a cClassTrib not linked to the NCM. */
public record CalculationError(String code, String detail) {}
