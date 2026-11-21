package com.ebravo.taxclassifier.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.RejectedLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublishProductsTest {

    private static final String HEADER = "code;description;ncm;price";

    private final List<Product> published = new ArrayList<>();
    private final PublishProducts publishProducts = new PublishProducts(published::addAll);

    @Test
    void validProductsArePublishedInFileOrder() {
        publishProducts.publish(List.of(HEADER, "P002;Feijao 1kg;07133319;8.49", "P001;Arroz 5kg;10063021;25.90"));

        assertThat(published).containsExactly(
                new Product("P002", "Feijao 1kg", "07133319", new BigDecimal("8.49")),
                new Product("P001", "Arroz 5kg", "10063021", new BigDecimal("25.90")));
    }

    @Test
    void rejectedLinesAreReturnedAndNeverPublished() {
        var result = publishProducts.publish(List.of(HEADER, "P001;Arroz 5kg;1006;25.90", "P002;Feijao 1kg;07133319;8.49"));

        assertThat(result.rejected()).containsExactly(new RejectedLine(2, "NCM must have 8 digits"));
        assertThat(published).isEqualTo(result.products()).extracting(Product::code).containsExactly("P002");
    }
}
