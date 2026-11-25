package com.ebravo.taxclassifier.domain;

import java.math.BigDecimal;

/** What the official calculator answered for a product, one case per estadoItem. */
public sealed interface TaxCalculation {

    /** The CBS and IBS amounts on the base, as the calculator rounded them. */
    record Calculated(BigDecimal base, BigDecimal cbs, BigDecimal ibs, boolean simulated) implements TaxCalculation {}
}
