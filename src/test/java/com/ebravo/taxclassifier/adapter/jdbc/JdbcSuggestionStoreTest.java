package com.ebravo.taxclassifier.adapter.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@JdbcTest
@Import(JdbcSuggestionStore.class)
@Testcontainers
class JdbcSuggestionStoreTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.7");

    private static final Product RICE = new Product("P001", "Arroz tipo 1 5kg", "10063021", new BigDecimal("25.90"));
    private static final Suggestion SUGGESTION =
            new Suggestion("200", "200003", new BigDecimal("0.90"), "Arroz integra a cesta básica.");

    @Autowired
    JdbcSuggestionStore store;

    @Test
    void savedSuggestionIsFoundWhateverThePrice() {
        store.save(RICE, SUGGESTION);

        // isEqualTo on the record compares the confidence with equals, so 0.90 must come back at scale 2.
        assertThat(store.find(new Product("P001", "Arroz tipo 1 5kg", "10063021", new BigDecimal("31.00"))))
                .contains(SUGGESTION);
    }

    @ParameterizedTest
    @CsvSource({
        "P002, Arroz tipo 1 5kg, 10063021",
        "P001, Arroz tipo 2 5kg, 10063021",
        "P001, Arroz tipo 1 5kg, 10063029",
        // A record on produtos skips the CSV's 8-digit check, so the NCM's end must not pass for the description's start.
        "P001, 1Arroz tipo 1 5kg, 1006302",
    })
    void otherCodeDescriptionOrNcmIsNotFound(String code, String description, String ncm) {
        store.save(RICE, SUGGESTION);

        assertThat(store.find(new Product(code, description, ncm, RICE.price()))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL, 10063021", "Arroz tipo 1 5kg, NULL"}, nullValues = "NULL")
    void productWithoutDescriptionOrNcmIsRejected(String description, String ncm) {
        // Hashed as the text "null", it would match a product whose description or NCM is that word.
        var product = new Product("P001", description, ncm, RICE.price());

        assertThatThrownBy(() -> store.save(product, SUGGESTION)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void secondSaveKeepsTheFirstSuggestion() {
        store.save(RICE, SUGGESTION);

        store.save(RICE, new Suggestion("220", "220001", new BigDecimal("0.95"), "Outra resposta."));

        assertThat(store.find(RICE)).contains(SUGGESTION);
    }
}
