package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.domain.Product;
import java.math.BigDecimal;

/** A product on {@code produtos}. The price travels as a string, so no consumer reads it as a double. */
record ProductMessage(String code, String description, String ncm, String price) {

    static ProductMessage of(Product product) {
        return new ProductMessage(product.code(), product.description(), product.ncm(), product.price().toPlainString());
    }

    Product toProduct() {
        return new Product(code, description, ncm, new BigDecimal(price));
    }
}
