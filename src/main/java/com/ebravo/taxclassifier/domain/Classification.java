package com.ebravo.taxclassifier.domain;

import com.ebravo.taxclassifier.domain.TaxCalculation.Calculated;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** What the classification flow concluded about a product: a classification to publish, or a review for an accountant. */
public sealed interface Classification {

    Suggestion suggestion();

    /** The legal basis is empty when the calculator publishes none for the cClassTrib; the calculation may be simulated. */
    record Classified(Suggestion suggestion, Optional<LegalBasis> legalBasis, Calculated calculation) implements Classification {

        public Classified {
            Objects.requireNonNull(legalBasis, "legalBasis");
            Objects.requireNonNull(calculation, "calculation");
        }
    }

    /** The errors are the calculator's, empty when the suggestion was sent to review before it was calculated. */
    record ToReview(Suggestion suggestion, ReviewReason reason, List<CalculationError> errors) implements Classification {

        public ToReview {
            Objects.requireNonNull(reason, "reason");
            errors = List.copyOf(errors);
        }
    }
}
