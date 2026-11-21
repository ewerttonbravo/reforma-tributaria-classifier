package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.Product;
import java.util.List;

public interface ProductPublisher {

    /** Publishes the products in the given order; returns once every one is accepted, or throws. */
    void publish(List<Product> products);
}
