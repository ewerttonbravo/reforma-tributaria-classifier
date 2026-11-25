package com.ebravo.taxclassifier.adapter.calculator;

import com.ebravo.taxclassifier.application.TaxCalculator;
import com.ebravo.taxclassifier.domain.CalculationError;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxCalculation;
import com.ebravo.taxclassifier.domain.TaxCalculation.Calculated;
import com.ebravo.taxclassifier.domain.TaxCalculation.InputInconsistency;
import com.ebravo.taxclassifier.domain.TaxCalculation.NotImplemented;
import com.ebravo.taxclassifier.domain.TaxCalculation.TechnicalFailure;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class CalculatorTaxCalculator implements TaxCalculator {

    // The same zone as the day of the official list, so the calculation and the list share a date.
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final RestClient calculator;
    private final long municipality;
    private final String uf;

    CalculatorTaxCalculator(
            RestClient.Builder builder,
            @Value("${calculator.base-url}") String baseUrl,
            @Value("${calculator.municipality}") long municipality,
            @Value("${calculator.uf}") String uf) {
        this.calculator = builder.baseUrl(baseUrl).build();
        this.municipality = municipality;
        this.uf = uf;
    }

    @Override
    public TaxCalculation calculate(Product product, Suggestion suggestion, Instant taxableEvent) {
        var item = new ItemRequest(1, product.ncm(), suggestion.cst(), suggestion.cClassTrib(), product.price());
        var request = new OperationRequest(product.code(), "1.0.0",
                taxableEvent.atZone(SAO_PAULO).toOffsetDateTime(), municipality, uf, List.of(item));
        var answer = calculator.post()
                .uri("/observabilidade/regime-geral")
                .body(request)
                .retrieve()
                .body(OperationResponse.class)
                .objetos()
                .getFirst();
        return switch (answer.estadoItem()) {
            case CALCULADO -> answer.calculated(false);
            case CALCULO_SIMULADO -> answer.calculated(true);
            case INCONSISTENCIA_ENTRADA -> new InputInconsistency(answer.errors());
            case NAO_IMPLEMENTADO -> new NotImplemented(answer.errors());
            case FALHA_TECNICA -> new TechnicalFailure(answer.errors());
        };
    }

    /** The calculator's field names; one item per product. */
    record OperationRequest(String id, String versao, OffsetDateTime dhFatoGerador, long municipio, String uf, List<ItemRequest> itens) {}

    record ItemRequest(int numero, String ncm, String cst, String cClassTrib, BigDecimal baseCalculo) {}

    record OperationResponse(List<ItemResponse> objetos) {}

    /** A state this adapter does not know fails the parse, so it never passes for a result. */
    enum ItemState { CALCULADO, CALCULO_SIMULADO, INCONSISTENCIA_ENTRADA, NAO_IMPLEMENTADO, FALHA_TECNICA }

    record ItemResponse(ItemState estadoItem, TaxesResponse tribCalc, List<ErrorResponse> erros) {

        Calculated calculated(boolean simulated) {
            var amounts = tribCalc.ibsCbs().gIBSCBS();
            return new Calculated(amounts.vBC(), amounts.gCBS().vCBS(), amounts.vIBS(), simulated);
        }

        List<CalculationError> errors() {
            return erros.stream().map(error -> new CalculationError(error.code(), error.detail())).toList();
        }
    }

    /** The type, title and pointer stay out: the code names the error and the detail explains it. */
    record ErrorResponse(String code, String detail) {}

    record TaxesResponse(@JsonProperty("IBSCBS") IbsCbsResponse ibsCbs) {}

    record IbsCbsResponse(AmountsResponse gIBSCBS) {}

    record AmountsResponse(BigDecimal vBC, BigDecimal vIBS, CbsResponse gCBS) {}

    record CbsResponse(BigDecimal vCBS) {}
}
