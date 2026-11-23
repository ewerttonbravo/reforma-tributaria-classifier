package com.ebravo.taxclassifier.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebravo.taxclassifier.domain.Verdict.Accepted;
import com.ebravo.taxclassifier.domain.Verdict.NeedsReview;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuggestionCheckTest {

    private static final List<TaxSituation> OFFICIAL = List.of(
            new TaxSituation("000", "Tributação integral", List.of(
                    new TaxClassification("000001", "Situações tributadas integralmente pelo IBS e CBS."))),
            new TaxSituation("200", "Alíquota reduzida", List.of(
                    new TaxClassification("200003", "Vendas de produtos destinados à alimentação humana (Anexo I)"))));

    private final SuggestionCheck check = new SuggestionCheck(new BigDecimal("0.80"));

    @Test
    void suggestionInsideOfficialListIsAccepted() {
        var suggestion = new Suggestion("200", "200003", new BigDecimal("0.95"));

        assertThat(check.check(suggestion, OFFICIAL)).isEqualTo(new Accepted(suggestion));
    }

    @Test
    void unknownCstGoesToReview() {
        var suggestion = new Suggestion("999", "999001", new BigDecimal("0.95"));

        assertThat(check.check(suggestion, OFFICIAL))
                .isEqualTo(new NeedsReview(suggestion, ReviewReason.OUTSIDE_OFFICIAL_LIST));
    }

    @Test
    void cClassTribUnderAnotherCstGoesToReview() {
        var suggestion = new Suggestion("000", "200003", new BigDecimal("0.95"));

        assertThat(check.check(suggestion, OFFICIAL))
                .isEqualTo(new NeedsReview(suggestion, ReviewReason.OUTSIDE_OFFICIAL_LIST));
    }

    @Test
    void emptyOfficialListSendsEverySuggestionToReview() {
        var suggestion = new Suggestion("000", "000001", new BigDecimal("0.95"));

        assertThat(check.check(suggestion, List.of()))
                .isEqualTo(new NeedsReview(suggestion, ReviewReason.OUTSIDE_OFFICIAL_LIST));
    }
}
