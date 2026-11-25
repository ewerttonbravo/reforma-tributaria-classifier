package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.CalculationError;
import java.util.List;
import java.util.stream.Collectors;

/** FALHA_TECNICA: the calculator failed on the item, so there is nothing to classify or review. */
public final class CalculationFailed extends RuntimeException {

    // The errors live in the message only: CalculationError is not Serializable, and the domain stays free of java.io.
    public CalculationFailed(List<CalculationError> errors) {
        super(errors.stream()
                .map(error -> error.code() + " " + error.detail())
                .collect(Collectors.joining("; ", "the calculator failed on the item: ", "")));
    }
}
