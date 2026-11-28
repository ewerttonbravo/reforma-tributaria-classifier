package com.ebravo.taxclassifier.domain;

public enum ReviewReason {
    OUTSIDE_OFFICIAL_LIST,
    LOW_CONFIDENCE,
    /** INCONSISTENCIA_ENTRADA, such as REG-011 for a cClassTrib not linked to the NCM. */
    INPUT_INCONSISTENCY,
    /** NAO_IMPLEMENTADO: the calculator cannot yet calculate the cClassTrib. */
    NOT_IMPLEMENTED,
    /** FALHA_TECNICA: the calculator failed on its own data, such as data missing from its database. */
    TECHNICAL_FAILURE
}
