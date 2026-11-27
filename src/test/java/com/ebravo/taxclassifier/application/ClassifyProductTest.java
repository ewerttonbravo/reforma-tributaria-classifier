package com.ebravo.taxclassifier.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebravo.taxclassifier.domain.CalculationError;
import com.ebravo.taxclassifier.domain.Classification.Classified;
import com.ebravo.taxclassifier.domain.Classification.ToReview;
import com.ebravo.taxclassifier.domain.LegalBasis;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.ReviewReason;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.SuggestionCheck;
import com.ebravo.taxclassifier.domain.TaxCalculation;
import com.ebravo.taxclassifier.domain.TaxCalculation.Calculated;
import com.ebravo.taxclassifier.domain.TaxCalculation.InputInconsistency;
import com.ebravo.taxclassifier.domain.TaxCalculation.NotImplemented;
import com.ebravo.taxclassifier.domain.TaxCalculation.TechnicalFailure;
import com.ebravo.taxclassifier.domain.TaxClassification;
import com.ebravo.taxclassifier.domain.TaxSituation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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
    private static final Calculated CALCULATED =
            new Calculated(new BigDecimal("25.90"), new BigDecimal("0.23"), new BigDecimal("0.03"), false);

    private final List<LocalDate> officialListDates = new ArrayList<>();
    private final List<String> calculations = new ArrayList<>();
    private final List<String> legalBasisRequests = new ArrayList<>();
    private final List<Object> advised = new ArrayList<>();
    private final Map<Product, Suggestion> saved = new HashMap<>();
    private Suggestion answer;
    private TaxCalculation calculation = CALCULATED;

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void calculatedSuggestionIsClassifiedWithItsAmountsAndLegalBasis(boolean simulated) {
        answer = new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica.");
        var amounts = new Calculated(new BigDecimal("25.90"), new BigDecimal("2.28"), new BigDecimal("0.03"), simulated);
        calculation = amounts;

        var classification = classifyAt(NOON_IN_SAO_PAULO).classify(PRODUCT);

        assertThat(classification).isEqualTo(new Classified(answer, Optional.of(BASIS_OF_200003), amounts));
        assertThat(advised).containsExactly(PRODUCT, OFFICIAL);
        assertThat(calculations).containsExactly("P001 under 200/200003 at " + NOON_IN_SAO_PAULO);
    }

    @Test
    void classificationWithNoPublishedLegalBasisIsStillClassified() {
        answer = new Suggestion("220", "220001", new BigDecimal("0.95"), "Incorporação imobiliária em regime especial.");

        var classification = classifyAt(NOON_IN_SAO_PAULO).classify(PRODUCT);

        assertThat(classification).isEqualTo(new Classified(answer, Optional.empty(), CALCULATED));
        assertThat(legalBasisRequests).containsExactly("220001 on " + TODAY);
    }

    @ParameterizedTest
    @CsvSource({
        "000, 200003, 0.95, OUTSIDE_OFFICIAL_LIST",
        "200, 200003, 0.79, LOW_CONFIDENCE",
    })
    void rejectedSuggestionGoesToReviewWithItsReason(String cst, String cClassTrib, BigDecimal confidence, ReviewReason reason) {
        answer = new Suggestion(cst, cClassTrib, confidence, "Arroz integra a cesta básica.");

        var classification = classifyAt(NOON_IN_SAO_PAULO).classify(PRODUCT);

        assertThat(classification).isEqualTo(new ToReview(answer, reason, List.of()));
        assertThat(calculations).isEmpty();
        assertThat(legalBasisRequests).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("calculatorRejections")
    void calculatorRejectionGoesToReviewWithItsErrors(TaxCalculation rejection, List<CalculationError> errors, ReviewReason reason) {
        answer = new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica.");
        calculation = rejection;

        var classification = classifyAt(NOON_IN_SAO_PAULO).classify(PRODUCT);

        assertThat(classification).isEqualTo(new ToReview(answer, reason, errors));
        assertThat(legalBasisRequests).isEmpty();
    }

    /** Errors as release 1.6.1 answered them; REG-036 and REG-037 are INCONSISTENCIA_ENTRADA too and take the same path. */
    static Stream<Arguments> calculatorRejections() {
        var notLinked = List.of(new CalculationError("REG-011", "NCM de código 10063021 não vinculada à Classificação Tributária de código 200035 (CBS e IBS)"));
        var notImplemented = List.of(new CalculationError("CAL-007", "A classificação tributária 620001 ainda não possui tratamento tributário implementado na Calculadora. Essa funcionalidade está prevista no roadmap e será disponibilizada em versão futura."));
        return Stream.of(
                Arguments.of(new InputInconsistency(notLinked), notLinked, ReviewReason.INPUT_INCONSISTENCY),
                Arguments.of(new NotImplemented(notImplemented), notImplemented, ReviewReason.NOT_IMPLEMENTED));
    }

    @Test
    void technicalFailureIsAFailureNotAReview() {
        answer = new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica.");
        calculation = new TechnicalFailure(List.of(
                new CalculationError("CAL-001", "Alíquota não encontrada"), new CalculationError("CAL-999", "Erro interno do sistema")));

        assertThatThrownBy(() -> classifyAt(NOON_IN_SAO_PAULO).classify(PRODUCT))
                .isInstanceOf(CalculationFailed.class)
                .hasMessage("the calculator failed on the item: CAL-001 Alíquota não encontrada; CAL-999 Erro interno do sistema");
        assertThat(legalBasisRequests).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "2027-03-11T02:59:59Z, 2027-03-10",
        "2027-03-11T03:00:00Z, 2027-03-11",
    })
    void officialListIsTheOneInForceTodayInSaoPaulo(Instant now, LocalDate today) {
        answer = new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica.");

        classifyAt(now).classify(PRODUCT);

        assertThat(officialListDates).containsExactly(today);
        assertThat(legalBasisRequests).containsExactly("200003 on " + today);
    }

    @Test
    void savedSuggestionIsUsedWithoutAskingTheAi() {
        var stored = new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica.");
        saved.put(PRODUCT, stored);

        var classification = classifyAt(NOON_IN_SAO_PAULO).classify(PRODUCT);

        assertThat(classification).isEqualTo(new Classified(stored, Optional.of(BASIS_OF_200003), CALCULATED));
        assertThat(advised).isEmpty();
    }

    @Test
    void newSuggestionIsSavedEvenWhenItGoesToReview() {
        answer = new Suggestion("200", "200003", new BigDecimal("0.79"), "Arroz integra a cesta básica.");

        classifyAt(NOON_IN_SAO_PAULO).classify(PRODUCT);

        assertThat(saved).containsExactly(Map.entry(PRODUCT, answer));
    }

    @Test
    void failedAiCallSavesNothing() {
        answer = null;

        assertThatThrownBy(() -> classifyAt(NOON_IN_SAO_PAULO).classify(PRODUCT)).isInstanceOf(AiFailure.class);
        assertThat(saved).isEmpty();
    }

    private ClassifyProduct classifyAt(Instant now) {
        return new ClassifyProduct(
                date -> {
                    officialListDates.add(date);
                    return OFFICIAL;
                },
                (product, official) -> {
                    advised.addAll(List.of(product, official));
                    if (answer == null) {
                        throw new AiFailure(AiFailure.Kind.REFUSED, "the AI refused", null);
                    }
                    return answer;
                },
                new SuggestionStore() {
                    @Override
                    public Optional<Suggestion> find(Product product) {
                        return Optional.ofNullable(saved.get(product));
                    }

                    @Override
                    public void save(Product product, Suggestion suggestion) {
                        saved.putIfAbsent(product, suggestion);
                    }
                },
                new SuggestionCheck(new BigDecimal("0.80")),
                (product, suggestion, taxableEvent) -> {
                    calculations.add(product.code() + " under " + suggestion.cst() + "/" + suggestion.cClassTrib() + " at " + taxableEvent);
                    return calculation;
                },
                (cClassTrib, date) -> {
                    legalBasisRequests.add(cClassTrib + " on " + date);
                    // Any other code or date has no basis, so asking for the wrong one fails the test.
                    return cClassTrib.equals("200003") && date.equals(TODAY) ? Optional.of(BASIS_OF_200003) : Optional.empty();
                },
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
