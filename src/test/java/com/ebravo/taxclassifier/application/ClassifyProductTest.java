package com.ebravo.taxclassifier.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebravo.taxclassifier.domain.Classification.Classified;
import com.ebravo.taxclassifier.domain.Classification.ToReview;
import com.ebravo.taxclassifier.domain.LegalBasis;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.ReviewReason;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.SuggestionCheck;
import com.ebravo.taxclassifier.domain.TaxClassification;
import com.ebravo.taxclassifier.domain.TaxSituation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClassifyProductTest {

    private static final Instant NOON_IN_SAO_PAULO = Instant.parse("2027-03-10T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    private static final Product PRODUCT = new Product("P001", "Arroz tipo 1 5kg", "10063021", new BigDecimal("25.90"));
    private static final List<TaxSituation> OFFICIAL = List.of(
            new TaxSituation("200", "Alíquota reduzida", List.of(
                    new TaxClassification("200003", "Vendas de produtos destinados à alimentação humana (Anexo I)"))),
            new TaxSituation("220", "Alíquota fixa", List.of(
                    new TaxClassification("220001", "Incorporação imobiliária submetida ao regime especial de tributação"))));
    private static final LegalBasis BASIS_OF_200003 = new LegalBasis("Art. 125", "Art. 125. Ficam reduzidas a zero", "LC 214/2025");

    private final List<LocalDate> officialListDates = new ArrayList<>();
    private final List<String> legalBasisRequests = new ArrayList<>();
    private final List<Object> advised = new ArrayList<>();
    private Suggestion answer;

    @Test
    void acceptedSuggestionIsClassifiedWithItsOfficialLegalBasis() {
        answer = new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica.");

        var classification = classifyAt(NOON_IN_SAO_PAULO).classify(PRODUCT);

        assertThat(classification).isEqualTo(new Classified(answer, Optional.of(BASIS_OF_200003)));
        assertThat(advised).containsExactly(PRODUCT, OFFICIAL);
    }

    @ParameterizedTest
    @CsvSource({
        "000, 200003, 0.95, OUTSIDE_OFFICIAL_LIST",
        "200, 200003, 0.79, LOW_CONFIDENCE",
    })
    void rejectedSuggestionGoesToReviewWithItsReason(String cst, String cClassTrib, BigDecimal confidence, ReviewReason reason) {
        answer = new Suggestion(cst, cClassTrib, confidence, "Arroz integra a cesta básica.");

        var classification = classifyAt(NOON_IN_SAO_PAULO).classify(PRODUCT);

        assertThat(classification).isEqualTo(new ToReview(answer, reason));
        assertThat(legalBasisRequests).isEmpty();
    }

    private ClassifyProduct classifyAt(Instant now) {
        return new ClassifyProduct(
                date -> {
                    officialListDates.add(date);
                    return OFFICIAL;
                },
                (product, official) -> {
                    advised.addAll(List.of(product, official));
                    return answer;
                },
                new SuggestionCheck(new BigDecimal("0.80")),
                (cClassTrib, date) -> {
                    legalBasisRequests.add(cClassTrib + " on " + date);
                    // Any other code or date has no basis, so asking for the wrong one fails the test.
                    return cClassTrib.equals("200003") && date.equals(TODAY) ? Optional.of(BASIS_OF_200003) : Optional.empty();
                },
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
