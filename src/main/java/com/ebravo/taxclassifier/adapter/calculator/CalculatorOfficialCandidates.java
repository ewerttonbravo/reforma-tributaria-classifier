package com.ebravo.taxclassifier.adapter.calculator;

import com.ebravo.taxclassifier.application.OfficialCandidates;
import com.ebravo.taxclassifier.domain.TaxClassification;
import com.ebravo.taxclassifier.domain.TaxSituation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class CalculatorOfficialCandidates implements OfficialCandidates {

    private final RestClient calculator;
    private final LatestDateCache<List<TaxSituation>> cache = new LatestDateCache<>(this::read);

    CalculatorOfficialCandidates(RestClient.Builder builder, @Value("${calculator.base-url}") String baseUrl) {
        this.calculator = builder.baseUrl(baseUrl).build();
    }

    @Override
    public List<TaxSituation> on(LocalDate date) {
        return cache.on(date);
    }

    private List<TaxSituation> read(LocalDate date) {
        return CalculatorCall.send(() -> calculator.get()
                        .uri("/dados-abertos/situacoes-tributarias/cbs-ibs?data={date}", date)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<SituationResponse>>() {}))
                .stream()
                .map(SituationResponse::toDomain)
                .toList();
    }

    /** The calculator's field names; its internal id is left out. */
    record SituationResponse(String codigo, String descricao, List<ClassificationResponse> classificacoesTributarias) {

        TaxSituation toDomain() {
            return new TaxSituation(codigo, descricao,
                    classificacoesTributarias.stream().map(ClassificationResponse::toDomain).toList());
        }
    }

    record ClassificationResponse(String codigo, String descricao) {

        TaxClassification toDomain() {
            return new TaxClassification(codigo, descricao);
        }
    }
}
