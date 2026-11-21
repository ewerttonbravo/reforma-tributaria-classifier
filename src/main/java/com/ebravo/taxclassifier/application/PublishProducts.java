package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.ProductCsv;
import com.ebravo.taxclassifier.domain.ProductCsvResult;
import java.util.List;

public final class PublishProducts {

    private final ProductPublisher publisher;

    public PublishProducts(ProductPublisher publisher) {
        this.publisher = publisher;
    }

    /** Publishes the valid products of the CSV lines and returns what was published and rejected. */
    public ProductCsvResult publish(List<String> csvLines) {
        var result = ProductCsv.parse(csvLines);
        publisher.publish(result.products());
        return result;
    }
}
