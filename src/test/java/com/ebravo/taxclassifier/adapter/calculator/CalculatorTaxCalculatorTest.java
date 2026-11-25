package com.ebravo.taxclassifier.adapter.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ebravo.taxclassifier.application.TaxCalculator;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxCalculation.Calculated;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
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

    private static String recorded(String name) {
        try {
            return new ClassPathResource("calculator/1.6.1/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
