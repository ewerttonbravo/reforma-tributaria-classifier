package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.adapter.cli.PublishCommand;
import com.ebravo.taxclassifier.application.ClassifyProduct;
import com.ebravo.taxclassifier.domain.CalculationError;
import com.ebravo.taxclassifier.domain.Classification.Classified;
import com.ebravo.taxclassifier.domain.Classification.ToReview;
import com.ebravo.taxclassifier.domain.LegalBasis;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.ReviewReason;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxCalculation.Calculated;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + PublishCommand.PROFILE)
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
        var product = message.toProduct();
        switch (classifyProduct.classify(product)) {
            case Classified(var suggestion, var legalBasis, var calculation) -> send(CLASSIFIED_TOPIC, product,
                    new ClassifiedMessage(message, SuggestionMessage.of(suggestion), legalBasis.orElse(null),
                            CalculationMessage.of(calculation)));
            case ToReview(var suggestion, var reason, var errors) -> send(REVIEW_TOPIC, product,
                    new ReviewMessage(message, SuggestionMessage.of(suggestion), reason, errors));
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

    /** Amounts as strings, like the price, so no consumer reads them as a double. */
    record CalculationMessage(String base, String cbs, String ibs, boolean simulated) {

        static CalculationMessage of(Calculated calculation) {
            return new CalculationMessage(calculation.base().toPlainString(), calculation.cbs().toPlainString(),
                    calculation.ibs().toPlainString(), calculation.simulated());
        }
    }

    record ClassifiedMessage(
            ProductMessage product, SuggestionMessage suggestion, LegalBasis legalBasis, CalculationMessage calculation) {}

    record ReviewMessage(
            ProductMessage product, SuggestionMessage suggestion, ReviewReason reason, List<CalculationError> errors) {}
}
