package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.adapter.kafka.KafkaProductPublisher.ProductMessage;
import com.ebravo.taxclassifier.application.ClassifyProduct;
import com.ebravo.taxclassifier.domain.Classification.Classified;
import com.ebravo.taxclassifier.domain.LegalBasis;
import com.ebravo.taxclassifier.domain.Product;
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
        if (classifyProduct.classify(product) instanceof Classified(var suggestion, var legalBasis)) {
            kafka.send(CLASSIFIED_TOPIC, product.code(),
                    new ClassifiedMessage(message, SuggestionMessage.of(suggestion), legalBasis.orElse(null))).join();
        }
    }

    record SuggestionMessage(String cst, String cClassTrib, String confidence, String justification) {

        static SuggestionMessage of(Suggestion suggestion) {
            return new SuggestionMessage(suggestion.cst(), suggestion.cClassTrib(),
                    suggestion.confidence().toPlainString(), suggestion.justification());
        }
    }

    record ClassifiedMessage(ProductMessage product, SuggestionMessage suggestion, LegalBasis legalBasis) {}
}
