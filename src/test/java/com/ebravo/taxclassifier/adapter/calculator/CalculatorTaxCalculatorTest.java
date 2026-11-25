package com.ebravo.taxclassifier.adapter.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ebravo.taxclassifier.application.TaxCalculator;
import com.ebravo.taxclassifier.domain.CalculationError;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxCalculation;
import com.ebravo.taxclassifier.domain.TaxCalculation.Calculated;
import com.ebravo.taxclassifier.domain.TaxCalculation.InputInconsistency;
import com.ebravo.taxclassifier.domain.TaxCalculation.NotImplemented;
import com.ebravo.taxclassifier.domain.TaxCalculation.TechnicalFailure;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;

/** Replays responses recorded from the official calculator at tag 1.6.1 for product P001 of the sample CSV. */
@RestClientTest(
        components = CalculatorTaxCalculator.class,
        properties = {
            "calculator.base-url=http://calculator/api/calculadora",
            "calculator.municipality=3550308",
            "calculator.uf=SP"
        })
class CalculatorTaxCalculatorTest {

    private static final String REGIME_GERAL = "http://calculator/api/calculadora/observabilidade/regime-geral";

    private static final Product RICE = new Product("P001", "Arroz tipo 1 5kg", "10063021", new BigDecimal("25.90"));

    private static final Suggestion FULL_TAXATION =
            new Suggestion("000", "000001", new BigDecimal("0.90"), "Tributação integral.");

    /** Already 2026-11-26 in UTC, still 2026-11-25 in São Paulo. */
    private static final Instant LATE_EVENING = Instant.parse("2026-11-26T02:30:00Z");

    @Autowired
    private TaxCalculator calculator;

    @Autowired
    private MockRestServiceServer server;

    /** A strict match: a field the request must not carry, such as nominal rates before 2027, fails it. */
    @Test
    void calculatedItemCarriesBaseCbsAndIbs() {
        server.expect(requestTo(REGIME_GERAL))
                .andExpect(method(POST))
                .andExpect(content().json(recorded("regime-geral-p001-2026-request.json"), JsonCompareMode.STRICT))
                .andRespond(withSuccess(recorded("regime-geral-p001-2026-calculado.json"), APPLICATION_JSON));

        var calculation = calculator.calculate(RICE, FULL_TAXATION, LATE_EVENING);

        server.verify();
        assertThat(calculation).isEqualTo(
                new Calculated(new BigDecimal("25.90"), new BigDecimal("0.23"), new BigDecimal("0.03"), false));
    }

    /** Recorded for 2027-01-04 with nominal rates of 8.8, 0.1 and 0.0; sending them is task 08b. */
    @Test
    void calculationOnNominalRatesIsFlaggedSimulated() {
        server.expect(requestTo(REGIME_GERAL))
                .andRespond(withSuccess(recorded("regime-geral-p001-2027-simulado.json"), APPLICATION_JSON));

        assertThat(calculator.calculate(RICE, FULL_TAXATION, LATE_EVENING)).isEqualTo(
                new Calculated(new BigDecimal("25.90"), new BigDecimal("2.28"), new BigDecimal("0.03"), true));
    }

    @ParameterizedTest
    @MethodSource("uncalculatedItems")
    void uncalculatedItemCarriesTheCalculatorErrors(String response, TaxCalculation expected) {
        server.expect(requestTo(REGIME_GERAL)).andRespond(withSuccess(response, APPLICATION_JSON));

        assertThat(calculator.calculate(RICE, FULL_TAXATION, LATE_EVENING)).isEqualTo(expected);
    }

    /**
     * REG-011 and CAL-007 were recorded (cClassTrib 200035 and 620001). No valid input made release 1.6.1 answer
     * FALHA_TECNICA, so that response is written in the shape of the recorded CAL-007, with the title of CAL-999 in its catalog.
     */
    static Stream<Arguments> uncalculatedItems() {
        return Stream.of(
                Arguments.of(recorded("regime-geral-p001-2026-reg011.json"), new InputInconsistency(List.of(new CalculationError(
                        "REG-011", "NCM de código 10063021 não vinculada à Classificação Tributária de código 200035 (CBS e IBS)")))),
                Arguments.of(recorded("regime-geral-p001-2026-nao-implementado.json"), new NotImplemented(List.of(new CalculationError(
                        "CAL-007", "A classificação tributária 620001 ainda não possui tratamento tributário implementado na Calculadora. Essa funcionalidade está prevista no roadmap e será disponibilizada em versão futura.")))),
                Arguments.of("""
                        {"objetos":[{"nObj":1,"estadoItem":"FALHA_TECNICA","erros":[{"code":"CAL-999","detail":"Erro interno do sistema"}]}]}
                        """, new TechnicalFailure(List.of(new CalculationError("CAL-999", "Erro interno do sistema")))));
    }

    private static String recorded(String name) {
        try {
            return new ClassPathResource("calculator/1.6.1/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
