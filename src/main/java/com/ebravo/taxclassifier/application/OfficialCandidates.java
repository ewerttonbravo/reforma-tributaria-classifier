package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.TaxSituation;
import java.time.LocalDate;
import java.util.List;

public interface OfficialCandidates {

    /** The official CBS/IBS CSTs and cClassTrib in force on the date; empty if none is, and throws if the list cannot be read:
     * {@link CalculatorUnavailable} if the calculator does not answer or answers with a server error. */
    List<TaxSituation> on(LocalDate date);
}
