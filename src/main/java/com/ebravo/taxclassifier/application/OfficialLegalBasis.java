package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.LegalBasis;
import java.time.LocalDate;
import java.util.Optional;

public interface OfficialLegalBasis {

    /** The legal basis of the cClassTrib in force on the date; empty if none is published, and throws if it cannot be
     * read: {@link CalculatorUnavailable} if the calculator does not answer or answers with a server error. */
    Optional<LegalBasis> of(String cClassTrib, LocalDate date);
}
