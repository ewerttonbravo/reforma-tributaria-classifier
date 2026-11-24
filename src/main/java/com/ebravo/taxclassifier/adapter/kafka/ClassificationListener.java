package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.adapter.kafka.KafkaProductPublisher.ProductMessage;
import com.ebravo.taxclassifier.application.ClassifyProduct;
import com.ebravo.taxclassifier.domain.Classification.Classified;
import com.ebravo.taxclassifier.domain.Classification.ToReview;
import com.ebravo.taxclassifier.domain.LegalBasis;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.ReviewReason;
import com.ebravo.taxclassifier.domain.Suggestion;
import java.math.BigDecimal;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!publish")
class ClassificationListener {

    static final String CLASSIFIED_TOPIC = "produtos-classificados";
    static final String REVIEW_TOPIC = "produtos-revisao";

    private final ClassifyProduct classifyProduct;
    private final KafkaTemplate<String, Object> kafka;

    ClassificationListener(ClassifyProduct classifyProduct, KafkaTemplate<String, Object> kafka) {
        this.classifyProduct = classifyProduct;
        this.kafka = kafka;
    }

    @KafkaListener(topics = KafkaProductPublisher.TOPIC)
    void classify(ProductMessage message) {
        var product = new Product(
                message.code(), message.description(), message.ncm(), new BigDecimal(message.price()));
        switch (classifyProduct.classify(product)) {
            case Classified(var suggestion, var legalBasis) -> send(CLASSIFIED_TOPIC, product,
                    new ClassifiedMessage(message, SuggestionMessage.of(suggestion), legalBasis.orElse(null)));
            case ToReview(var suggestion, var reason) -> send(REVIEW_TOPIC, product,
                    new ReviewMessage(message, SuggestionMessage.of(suggestion), reason));
        }
    }

    // Waits for the send, so a failed send reaches the error handler instead of being lost behind a committed offset.
    private void send(String topic, Product product, Object message) {
        kafka.send(topic, product.code(), message).join();
    }

    record SuggestionMessage(String cst, String cClassTrib, String confidence, String justification) {

        static SuggestionMessage of(Suggestion suggestion) {
            return new SuggestionMessage(suggestion.cst(), suggestion.cClassTrib(),
                    suggestion.confidence().toPlainString(), suggestion.justification());
        }
    }

    record ClassifiedMessage(ProductMessage product, SuggestionMessage suggestion, LegalBasis legalBasis) {}

    record ReviewMessage(ProductMessage product, SuggestionMessage suggestion, ReviewReason reason) {}
}
