package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.domain.Product;
import java.math.BigDecimal;
import java.util.Objects;

/** A product on {@code produtos}. The price travels as a string, so no consumer reads it as a double. */
record ProductMessage(String code, String description, String ncm, String price) {

    static ProductMessage of(Product product) {
        return new ProductMessage(product.code(), product.description(), product.ncm(), product.price().toPlainString());
    }

    /** A missing field fails with its name, which review carries as the cause. */
    Product toProduct() {
        return new Product(required(code, "code"), required(description, "description"), required(ncm, "ncm"),
                new BigDecimal(required(price, "price")));
    }

    private static String required(String field, String name) {
        return Objects.requireNonNull(field, () -> "the product has no " + name);
    }
}
