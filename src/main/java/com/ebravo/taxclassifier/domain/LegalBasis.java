package com.ebravo.taxclassifier.domain;

/** The official legal basis of a cClassTrib, as the calculator publishes it. */
public record LegalBasis(String shortText, String text, String normativeReference) {}
