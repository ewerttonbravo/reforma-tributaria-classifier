package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.application.ProductPublisher;
import com.ebravo.taxclassifier.domain.Product;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class KafkaProductPublisher implements ProductPublisher {

    static final String TOPIC = "produtos";

    private final KafkaTemplate<String, ProductMessage> kafka;

    KafkaProductPublisher(KafkaTemplate<String, ProductMessage> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publish(List<Product> products) {
        // Sends them all before waiting, so the producer batches; join rethrows the first failure.
        var sends = products.stream()
                .map(product -> kafka.send(TOPIC, product.code(), ProductMessage.of(product)))
                .toList();
        sends.forEach(CompletableFuture::join);
    }

    /** The price travels as a string, so no consumer reads it as a double. */
    record ProductMessage(String code, String description, String ncm, String price) {

        static ProductMessage of(Product product) {
            return new ProductMessage(product.code(), product.description(), product.ncm(), product.price().toPlainString());
        }
    }
}
