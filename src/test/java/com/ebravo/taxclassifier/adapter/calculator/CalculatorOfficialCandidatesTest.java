package com.ebravo.taxclassifier.adapter.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ebravo.taxclassifier.application.OfficialCandidates;
import com.ebravo.taxclassifier.domain.TaxClassification;
import com.ebravo.taxclassifier.domain.TaxSituation;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.client.MockRestServiceServer;

/** Replays responses recorded from the official calculator at tag 1.6.1. */
@RestClientTest(
        components = CalculatorOfficialCandidates.class,
        properties = "calculator.base-url=http://calculator/api/calculadora")
class CalculatorOfficialCandidatesTest {

    private static final String LIST_FOR_DATE =
            "http://calculator/api/calculadora/dados-abertos/situacoes-tributarias/cbs-ibs?data=";

    @Autowired
    private OfficialCandidates candidates;

    @Autowired
    private MockRestServiceServer calculator;

    @Test
    void officialListForTheDateComesFromTheCalculator() {
        calculator.expect(requestTo(LIST_FOR_DATE + "2027-03-10"))
                .andExpect(method(GET))
                .andRespond(withSuccess(recorded("situacoes-tributarias-cbs-ibs-2027-03-10.json"), APPLICATION_JSON));

        var situations = candidates.on(LocalDate.of(2027, 3, 10));

        calculator.verify();
        assertThat(situations).hasSize(18);
        assertThat(situations).flatExtracting(TaxSituation::classifications).hasSize(164);
        assertThat(situations.getFirst()).isEqualTo(new TaxSituation("000", "Tributação integral", List.of(
                new TaxClassification("000001", "Situações tributadas integralmente pelo IBS e CBS."),
                new TaxClassification("000002", "Exploração de via"),
                new TaxClassification("000003", "Regime automotivo - projetos incentivados (art. 311)"),
                new TaxClassification("000004", "Regime automotivo - projetos incentivados (art. 312)"),
                new TaxClassification("000005",
                        "Operação com EAC destinado à mistura com gasolina A, mas com saída do biocombustível com destinação diversa"))));
        assertThat(situations.getLast()).isEqualTo(new TaxSituation("830", "Exclusão de base de cálculo", List.of(
                new TaxClassification("830001",
                        "Documento com exclusão da BC da CBS e do IBS de energia elétrica fornecida pela distribuidora à UC"))));
    }

    private static ClassPathResource recorded(String file) {
        return new ClassPathResource("calculator/1.6.1/" + file);
    }
}
