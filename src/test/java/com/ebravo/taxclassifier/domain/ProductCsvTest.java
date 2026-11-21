package com.ebravo.taxclassifier.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProductCsvTest {

    private static final String HEADER = "code;description;ncm;price";

    @Test
    void validLineBecomesAProduct() {
        var result = ProductCsv.parse(List.of(HEADER, "P001;Arroz tipo 1 5kg;10063021;25.90"));

        assertThat(result.products())
                .containsExactly(new Product("P001", "Arroz tipo 1 5kg", "10063021", new BigDecimal("25.90")));
        assertThat(result.rejected()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"7, 7.00", "10.5, 10.50", "0.01, 0.01"})
    void priceIsKeptWithScaleTwo(String price, String expected) {
        var result = ProductCsv.parse(List.of(HEADER, "P001;Arroz;10063021;" + price));

        assertThat(result.products()).singleElement()
                .extracting(Product::price)
                .satisfies(actual -> assertThat(actual.toPlainString()).isEqualTo(expected));
    }
}
