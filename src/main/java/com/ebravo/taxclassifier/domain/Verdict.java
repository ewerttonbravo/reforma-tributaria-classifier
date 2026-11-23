package com.ebravo.taxclassifier.domain;

/** Whether an AI suggestion may stand or goes to an accountant for review. */
public sealed interface Verdict {

    Suggestion suggestion();

    record Accepted(Suggestion suggestion) implements Verdict {}

    record NeedsReview(Suggestion suggestion, ReviewReason reason) implements Verdict {}
}
