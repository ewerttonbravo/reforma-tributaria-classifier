package com.ebravo.taxclassifier.adapter.calculator;

import com.ebravo.taxclassifier.application.OfficialLegalBasis;
import com.ebravo.taxclassifier.domain.LegalBasis;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class CalculatorOfficialLegalBasis implements OfficialLegalBasis {

    private final RestClient calculator;

    CalculatorOfficialLegalBasis(RestClient.Builder builder, @Value("${calculator.base-url}") String baseUrl) {
        this.calculator = builder.baseUrl(baseUrl).build();
    }

    /** Release 1.6.1 lists the legal bases by date only, so the cClassTrib is picked here. */
    @Override
    public Optional<LegalBasis> of(String cClassTrib, LocalDate date) {
        return calculator.get()
                .uri("/dados-abertos/fundamentacoes-legais?data={date}", date)
                .retrieve()
                .body(new ParameterizedTypeReference<List<LegalBasisResponse>>() {})
                .stream()
                .filter(basis -> basis.codigoClassificacaoTributaria().equals(cClassTrib))
                .findFirst()
                .map(LegalBasisResponse::toDomain);
    }

    /** The calculator's field names; the CST, the descriptions and the tax set stay out. */
    record LegalBasisResponse(String codigoClassificacaoTributaria, String textoCurto, String texto, String referenciaNormativa) {

        LegalBasis toDomain() {
            return new LegalBasis(textoCurto, texto, referenciaNormativa);
        }
    }
}
