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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class CalculatorTaxCalculator implements TaxCalculator {

    // The same zone as the day of the official list, so the calculation and the list share a date.
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    // Release 1.6.1 resolves the rates before this year and forbids sending them (REG-036); from it on, requires them (REG-037).
    private static final int FIRST_YEAR_OF_NOMINAL_RATES = 2027;

    private final RestClient calculator;
    private final long municipality;
    private final String uf;
    private final NominalRates nominalRates;

    CalculatorTaxCalculator(
            RestClient.Builder builder,
            @Value("${calculator.base-url}") String baseUrl,
            @Value("${calculator.municipality}") long municipality,
            @Value("${calculator.uf}") String uf,
            @Value("${calculator.nominal-rates.cbs}") BigDecimal cbs,
            @Value("${calculator.nominal-rates.ibs-state}") BigDecimal ibsState,
            @Value("${calculator.nominal-rates.ibs-municipal}") BigDecimal ibsMunicipal) {
        this.calculator = builder.baseUrl(baseUrl).build();
        this.municipality = municipality;
        this.uf = uf;
        this.nominalRates = new NominalRates(cbs, ibsState, ibsMunicipal);
    }

    @Override
    public TaxCalculation calculate(Product product, Suggestion suggestion, Instant taxableEvent) {
        // Release 1.6.1 rejects fractions of a second; cutting them never changes the day or the year.
        var taxableEventInSaoPaulo = taxableEvent.truncatedTo(ChronoUnit.SECONDS).atZone(SAO_PAULO).toOffsetDateTime();
        var rates = taxableEventInSaoPaulo.getYear() >= FIRST_YEAR_OF_NOMINAL_RATES ? nominalRates : null;
        var item = new ItemRequest(1, product.ncm(), suggestion.cst(), suggestion.cClassTrib(), product.price(), rates);
        var request = new OperationRequest(product.code(), "1.0.0", taxableEventInSaoPaulo, municipality, uf, List.of(item));
        var response = CalculatorCall.send(() -> calculator.post()
                .uri("/observabilidade/regime-geral")
                .body(request)
                .retrieve()
                .body(OperationResponse.class));
        var answer = response.objetos().getFirst();
        return switch (answer.estadoItem()) {
            case CALCULADO -> response.calculated(false);
            case CALCULO_SIMULADO -> response.calculated(true);
            case INCONSISTENCIA_ENTRADA -> new InputInconsistency(answer.errors());
            case NAO_IMPLEMENTADO -> new NotImplemented(answer.errors());
            case FALHA_TECNICA -> new TechnicalFailure(answer.errors());
        };
    }

    /** The calculator's field names; one item per product. */
    record OperationRequest(String id, String versao, OffsetDateTime dhFatoGerador, long municipio, String uf, List<ItemRequest> itens) {}

    record ItemRequest(
            int numero, String ncm, String cst, String cClassTrib, BigDecimal baseCalculo,
            @JsonInclude(JsonInclude.Include.NON_NULL) NominalRates aliquotasNominais) {}

    /** In percent, as the calculator takes them. */
    record NominalRates(BigDecimal cbs, BigDecimal ibsEstadual, BigDecimal ibsMunicipal) {}

    /**
     * The amounts are read from the operation's total, which is the item's own as the operation has one item: release 1.6.1
     * leaves them off the item for an immunity (CST 410) but always totals them.
     */
    record OperationResponse(List<ItemResponse> objetos, TotalResponse total) {

        Calculated calculated(boolean simulated) {
            var amounts = total.tribCalc().ibsCbs();
            return new Calculated(amounts.vBCIBSCBS(), amounts.gCBS().vCBS(), amounts.gIBS().vIBS(), simulated);
        }
    }

    /** A state this adapter does not know fails the parse, so it never passes for a result. */
    enum ItemState { CALCULADO, CALCULO_SIMULADO, INCONSISTENCIA_ENTRADA, NAO_IMPLEMENTADO, FALHA_TECNICA }

    record ItemResponse(ItemState estadoItem, List<ErrorResponse> erros) {

        List<CalculationError> errors() {
            return erros.stream().map(error -> new CalculationError(error.code(), error.detail())).toList();
        }
    }

    /** The type, title and pointer stay out: the code names the error and the detail explains it. */
    record ErrorResponse(String code, String detail) {}

    record TotalResponse(TotalTaxesResponse tribCalc) {}

    record TotalTaxesResponse(@JsonProperty("IBSCBSTot") TotalAmountsResponse ibsCbs) {}

    record TotalAmountsResponse(BigDecimal vBCIBSCBS, CbsResponse gCBS, IbsResponse gIBS) {}

    record CbsResponse(BigDecimal vCBS) {}

    record IbsResponse(BigDecimal vIBS) {}
}
