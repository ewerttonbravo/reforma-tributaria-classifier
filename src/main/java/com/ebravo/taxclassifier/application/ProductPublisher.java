package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.Product;
import java.util.List;

public interface ProductPublisher {

    /** Publishes the products, keeping the order of updates to the same code; returns once every one is accepted, or throws. */
    void publish(List<Product> products);
}
