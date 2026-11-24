package com.ebravo.taxclassifier.domain;

import java.util.Optional;

/** What the classification flow concluded about a product: a classification to publish, or a review for an accountant. */
public sealed interface Classification {

    Suggestion suggestion();

    /** The legal basis is empty when the calculator publishes none for the cClassTrib. */
    record Classified(Suggestion suggestion, Optional<LegalBasis> legalBasis) implements Classification {}

    record ToReview(Suggestion suggestion, ReviewReason reason) implements Classification {}
}
