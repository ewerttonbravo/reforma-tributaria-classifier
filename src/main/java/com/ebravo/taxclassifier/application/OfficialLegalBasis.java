package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.LegalBasis;
import java.time.LocalDate;
import java.util.Optional;

public interface OfficialLegalBasis {

    /** The legal basis of the cClassTrib in force on the date; empty if none is published, and throws if it cannot be read. */
    Optional<LegalBasis> of(String cClassTrib, LocalDate date);
}
