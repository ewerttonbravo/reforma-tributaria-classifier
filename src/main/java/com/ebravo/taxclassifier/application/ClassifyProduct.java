package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.Classification;
import com.ebravo.taxclassifier.domain.Classification.Classified;
import com.ebravo.taxclassifier.domain.Classification.ToReview;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.ReviewReason;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.SuggestionCheck;
import com.ebravo.taxclassifier.domain.TaxCalculation.Calculated;
import com.ebravo.taxclassifier.domain.TaxCalculation.InputInconsistency;
import com.ebravo.taxclassifier.domain.TaxCalculation.NotImplemented;
import com.ebravo.taxclassifier.domain.TaxCalculation.TechnicalFailure;
import com.ebravo.taxclassifier.domain.TaxSituation;
import com.ebravo.taxclassifier.domain.Verdict.Accepted;
import com.ebravo.taxclassifier.domain.Verdict.NeedsReview;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Asks the AI to classify a product from the official list of the day, unless a suggestion is saved for it, checks the suggestion
 * and calculates CBS and IBS under it.
 */
public final class ClassifyProduct {

    // The official tables are read for the calendar day in Brazil, whatever zone the clock runs in.
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final OfficialCandidates candidates;
    private final AiAdvisor advisor;
    private final SuggestionStore suggestions;
    private final SuggestionCheck check;
    private final TaxCalculator calculator;
    private final OfficialLegalBasis legalBasis;
    private final Clock clock;

    public ClassifyProduct(
            OfficialCandidates candidates, AiAdvisor advisor, SuggestionStore suggestions, SuggestionCheck check,
            TaxCalculator calculator, OfficialLegalBasis legalBasis, Clock clock) {
        this.candidates = candidates;
        this.advisor = advisor;
        this.suggestions = suggestions;
        this.check = check;
        this.calculator = calculator;
        this.legalBasis = legalBasis;
        this.clock = clock;
    }

    public Classification classify(Product product) {
        // One instant, so the official list and the calculation share a day.
        var now = clock.instant();
        var today = LocalDate.ofInstant(now, SAO_PAULO);
        var official = candidates.on(today);
        var answer = suggestions.find(product).orElseGet(() -> ask(product, official));
        return switch (check.check(answer, official)) {
            case Accepted(var suggestion) -> calculate(product, suggestion, now, today);
            case NeedsReview(var suggestion, var reason) -> new ToReview(suggestion, reason, List.of());
        };
    }

    /**
     * Saved as soon as the AI answers, so a product read again after a crash is not paid for twice. Another instance may have saved
     * its own answer meanwhile, and that one stands.
     */
    private Suggestion ask(Product product, List<TaxSituation> official) {
        return suggestions.save(product, advisor.suggest(product, official));
    }

    /** Only a calculated suggestion stands, so only it needs its legal basis. */
    private Classification calculate(Product product, Suggestion suggestion, Instant now, LocalDate today) {
        return switch (calculator.calculate(product, suggestion, now)) {
            case Calculated calculation -> new Classified(suggestion, legalBasis.of(suggestion.cClassTrib(), today), calculation);
            case InputInconsistency(var errors) -> new ToReview(suggestion, ReviewReason.INPUT_INCONSISTENCY, errors);
            case NotImplemented(var errors) -> new ToReview(suggestion, ReviewReason.NOT_IMPLEMENTED, errors);
            case TechnicalFailure(var errors) -> throw new CalculationFailed(errors);
        };
    }
}
