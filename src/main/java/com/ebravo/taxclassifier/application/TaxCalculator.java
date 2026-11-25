package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxCalculation;
import java.time.Instant;

public interface TaxCalculator {

    /** CBS and IBS of the product under the suggested CST and cClassTrib, for a taxable event at the instant; throws if the calculator cannot be asked. */
    TaxCalculation calculate(Product product, Suggestion suggestion, Instant taxableEvent);
}
