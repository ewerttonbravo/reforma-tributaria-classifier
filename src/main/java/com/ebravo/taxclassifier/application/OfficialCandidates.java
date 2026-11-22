package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.TaxSituation;
import java.time.LocalDate;
import java.util.List;

public interface OfficialCandidates {

    /** The official CBS/IBS CSTs and cClassTrib in force on the date; empty if none is, and throws if the list cannot be read. */
    List<TaxSituation> on(LocalDate date);
}
