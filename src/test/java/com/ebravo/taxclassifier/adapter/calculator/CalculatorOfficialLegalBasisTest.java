package com.ebravo.taxclassifier.adapter.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ebravo.taxclassifier.application.OfficialLegalBasis;
import com.ebravo.taxclassifier.domain.LegalBasis;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientResponseException;

/** Replays the legal bases recorded from the official calculator at tag 1.6.1 for 2027-03-10. */
@RestClientTest(
        components = CalculatorOfficialLegalBasis.class,
        properties = "calculator.base-url=http://calculator/api/calculadora")
// The adapter keeps the last date read, so each test gets its own.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CalculatorOfficialLegalBasisTest {

    private static final String BASES_FOR_DATE =
            "http://calculator/api/calculadora/dados-abertos/fundamentacoes-legais?data=2027-03-10";

    private static final LocalDate DATE = LocalDate.of(2027, 3, 10);

    @Autowired
    private OfficialLegalBasis legalBasis;

    @Autowired
    private MockRestServiceServer calculator;

    /** The calculator writes line breaks as a backslash and an n; they are kept as published. */
    @Test
    void legalBasisOfTheClassificationComesFromTheCalculator() {
        calculator.expect(requestTo(BASES_FOR_DATE))
                .andExpect(method(GET))
                .andRespond(withSuccess(recorded(), APPLICATION_JSON));

        var basis = legalBasis.of("000002", DATE);

        calculator.verify();
        assertThat(basis).contains(new LegalBasis(
                "Art. 11, VIII",
                "Art. 11. Considera-se local da operação com:\\nVIII - serviço de exploração de via, mediante cobrança de valor a qualquer título, incluindo tarifas, pedágios e quaisquer outras formas de cobrança, o território de cada Município e Estado, ou do Distrito Federal, proporcionalmente à correspondente extensão da via explorada;",
                "LIVRO I \\n\\nDO IMPOSTO SOBRE BENS E SERVIÇOS (IBS) E DA CONTRIBUIÇÃO SOCIAL SOBRE BENS E SERVIÇOS (CBS) \\n\\nTÍTULO I \\n\\nDAS NORMAS GERAIS DO IBS E DA CBS \\n\\nCAPÍTULO II\\n\\nDO IBS E DA CBS SOBRE OPERAÇÕES COM BENS E SERVIÇOS\\n\\nSeção IV\\n\\nDo Local da Operação"));
    }

    /** 220001 is in the official list for the date, but the calculator publishes no legal basis for it. */
    @Test
    void classificationWithNoOfficialLegalBasisHasNone() {
        calculator.expect(requestTo(BASES_FOR_DATE)).andRespond(withSuccess(recorded(), APPLICATION_JSON));

        var basis = legalBasis.of("220001", DATE);

        calculator.verify();
        assertThat(basis).isEmpty();
    }

    /** Two codes of the same date share the date's list: the server expects one request. */
    @Test
    void legalBasesAreReadOncePerDate() {
        calculator.expect(requestTo(BASES_FOR_DATE)).andRespond(withSuccess(recorded(), APPLICATION_JSON));

        var withBasis = legalBasis.of("000002", DATE);
        var withoutBasis = legalBasis.of("220001", DATE);

        calculator.verify();
        assertThat(withBasis).map(LegalBasis::shortText).contains("Art. 11, VIII");
        assertThat(withoutBasis).isEmpty();
    }

    @Test
    void calculatorErrorIsNeverAMissingLegalBasis() {
        calculator.expect(requestTo(BASES_FOR_DATE)).andRespond(withServerError());

        assertThatThrownBy(() -> legalBasis.of("000002", DATE))
                .isInstanceOfSatisfying(RestClientResponseException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(500));
    }

    /** A null code is a caller's bug, so it must not get the official answer of 220001; no request is expected. */
    @Test
    void nullClassificationIsAnErrorNotAMissingLegalBasis() {
        assertThatThrownBy(() -> legalBasis.of(null, DATE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cClassTrib");
    }

    private static ClassPathResource recorded() {
        return new ClassPathResource("calculator/1.6.1/fundamentacoes-legais-2027-03-10.json");
    }
}
