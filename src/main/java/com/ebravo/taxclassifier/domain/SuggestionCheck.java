package com.ebravo.taxclassifier.domain;

import com.ebravo.taxclassifier.domain.Verdict.Accepted;
import com.ebravo.taxclassifier.domain.Verdict.NeedsReview;
import java.math.BigDecimal;
import java.util.List;

/** Checks an AI suggestion against the official list of the date. */
public final class SuggestionCheck {

    private final BigDecimal threshold;

    /** The threshold is the lowest confidence accepted, from 0 to 1. */
    public SuggestionCheck(BigDecimal threshold) {
        this.threshold = UnitInterval.require(threshold, "threshold");
    }

    public Verdict check(Suggestion suggestion, List<TaxSituation> official) {
        if (!isOfficial(suggestion, official)) {
            return new NeedsReview(suggestion, ReviewReason.OUTSIDE_OFFICIAL_LIST);
        }
        if (suggestion.confidence().compareTo(threshold) < 0) {
            return new NeedsReview(suggestion, ReviewReason.LOW_CONFIDENCE);
        }
        return new Accepted(suggestion);
    }

    private static boolean isOfficial(Suggestion suggestion, List<TaxSituation> official) {
        return official.stream()
                .filter(situation -> situation.cst().equals(suggestion.cst()))
                .flatMap(situation -> situation.classifications().stream())
                .anyMatch(classification -> classification.code().equals(suggestion.cClassTrib()));
    }
}
