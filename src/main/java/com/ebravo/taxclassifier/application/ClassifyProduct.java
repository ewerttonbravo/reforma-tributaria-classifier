package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.Classification;
import com.ebravo.taxclassifier.domain.Classification.Classified;
import com.ebravo.taxclassifier.domain.Classification.ToReview;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.SuggestionCheck;
import com.ebravo.taxclassifier.domain.Verdict.Accepted;
import com.ebravo.taxclassifier.domain.Verdict.NeedsReview;
import java.time.Clock;
import java.time.LocalDate;

/** Asks the AI to classify a product from the official list of the day and checks its answer. */
public final class ClassifyProduct {

    private final OfficialCandidates candidates;
    private final AiAdvisor advisor;
    private final SuggestionCheck check;
    private final OfficialLegalBasis legalBasis;
    private final Clock clock;

    public ClassifyProduct(
            OfficialCandidates candidates, AiAdvisor advisor, SuggestionCheck check, OfficialLegalBasis legalBasis, Clock clock) {
        this.candidates = candidates;
        this.advisor = advisor;
        this.check = check;
        this.legalBasis = legalBasis;
        this.clock = clock;
    }

    public Classification classify(Product product) {
        var today = LocalDate.now(clock);
        var official = candidates.on(today);
        return switch (check.check(advisor.suggest(product, official), official)) {
            case Accepted(var suggestion) -> new Classified(suggestion, legalBasis.of(suggestion.cClassTrib(), today));
            case NeedsReview(var suggestion, var reason) -> new ToReview(suggestion, reason);
        };
    }
}
