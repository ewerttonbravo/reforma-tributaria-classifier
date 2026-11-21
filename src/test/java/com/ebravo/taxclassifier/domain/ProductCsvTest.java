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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "P001;Arroz;10063021              | expected 4 fields separated by ';', found 3",
        "P001;Arroz;10063021;25.90;extra  | expected 4 fields separated by ';', found 5",
        "P001;Arroz;10063021;25.90;       | expected 4 fields separated by ';', found 5",
        "''                               | expected 4 fields separated by ';', found 1",
        ";Arroz;10063021;25.90            | code is blank",
        "' ;Arroz;10063021;25.90'         | code is blank",
        "P001;;10063021;25.90             | description is blank",
        "'P001; ;10063021;25.90'          | description is blank",
        "P001;Arroz;1006302;25.90         | NCM must have 8 digits",
        "P001;Arroz;100630211;25.90       | NCM must have 8 digits",
        "P001;Arroz;1006.30.21;25.90      | NCM must have 8 digits",
        "P001;Arroz;1006302A;25.90        | NCM must have 8 digits",
        "'P001;Arroz; 10063021;25.90'     | NCM must have 8 digits",
        "P001;Arroz;10063021;             | price must be a decimal number like 12.50",
        "P001;Arroz;10063021;abc          | price must be a decimal number like 12.50",
        "P001;Arroz;10063021;1E3          | price must be a decimal number like 12.50",
        "P001;Arroz;10063021;-1           | price must be a decimal number like 12.50",
        "'P001;Arroz;10063021; 5'         | price must be a decimal number like 12.50",
        "P001;Arroz;10063021;10.005       | price has more than 2 decimal places",
        "P001;Arroz;10063021;0            | price must be greater than zero",
        "P001;Arroz;10063021;0.00         | price must be greater than zero",
    })
    void invalidLineIsRejectedWithItsLineNumberAndReason(String line, String reason) {
        var result = ProductCsv.parse(List.of(HEADER, line));

        assertThat(result.products()).isEmpty();
        assertThat(result.rejected()).containsExactly(new RejectedLine(2, reason));
    }

    @Test
    void wrongHeaderRejectsEveryLineSoSwappedColumnsAreNeverRead() {
        var result = ProductCsv.parse(List.of("description;code;ncm;price", "Arroz;P001;10063021;25.90"));

        assertThat(result.rejected()).containsExactly(
                new RejectedLine(1, "expected header 'code;description;ncm;price'"),
                new RejectedLine(2, "not read: the header is wrong"));
        assertThat(result.products()).isEmpty();
    }

    @Test
    void emptyInputIsRejectedAsAMissingHeader() {
        var result = ProductCsv.parse(List.of());

        assertThat(result.rejected())
                .containsExactly(new RejectedLine(1, "expected header 'code;description;ncm;price'"));
        assertThat(result.products()).isEmpty();
    }

    @Test
    void everyDataLineEndsUpAsAProductOrARejectionInFileOrder() {
        var result = ProductCsv.parse(List.of(
                HEADER,
                "P001;Arroz;10063021;25.90",
                "P002;Feijao;0713",
                "P001;Arroz integral;10063021;27.90",
                "P003;Cerveja;22030000;0"));

        assertThat(result.products()).extracting(Product::description)
                .containsExactly("Arroz", "Arroz integral");
        assertThat(result.rejected()).extracting(RejectedLine::lineNumber).containsExactly(3, 5);
    }
}
