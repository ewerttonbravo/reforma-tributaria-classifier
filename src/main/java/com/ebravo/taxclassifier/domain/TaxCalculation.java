package com.ebravo.taxclassifier.domain;

import java.math.BigDecimal;
import java.util.List;

/** What the official calculator answered for a product, one case per estadoItem. */
public sealed interface TaxCalculation {

    /** The CBS and IBS amounts on the base, as the calculator rounded them. */
    record Calculated(BigDecimal base, BigDecimal cbs, BigDecimal ibs, boolean simulated) implements TaxCalculation {}

    /** INCONSISTENCIA_ENTRADA: the calculator rejected the item as sent, REG-011 among others. */
    record InputInconsistency(List<CalculationError> errors) implements TaxCalculation {}

    /** NAO_IMPLEMENTADO: the calculator cannot yet calculate the cClassTrib. */
    record NotImplemented(List<CalculationError> errors) implements TaxCalculation {}

    /** FALHA_TECNICA: the calculator failed on the item. */
    record TechnicalFailure(List<CalculationError> errors) implements TaxCalculation {}
}
