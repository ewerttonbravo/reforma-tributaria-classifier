package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.application.AiAdvisor;
import com.ebravo.taxclassifier.application.AiUnavailable;
import com.ebravo.taxclassifier.application.CalculatorUnavailable;
import com.ebravo.taxclassifier.application.ClassifyProduct;
import com.ebravo.taxclassifier.application.OfficialCandidates;
import com.ebravo.taxclassifier.application.OfficialLegalBasis;
import com.ebravo.taxclassifier.application.SuggestionStore;
import com.ebravo.taxclassifier.application.TaxCalculator;
import com.ebravo.taxclassifier.domain.SuggestionCheck;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
class ClassificationConfiguration {

    @Bean
    ClassifyProduct classifyProduct(OfficialCandidates candidates, AiAdvisor advisor, SuggestionStore suggestions,
            TaxCalculator calculator, OfficialLegalBasis legalBasis,
            @Value("${classification.confidence-threshold}") BigDecimal threshold) {
        return new ClassifyProduct(candidates, advisor, suggestions, new SuggestionCheck(threshold), calculator, legalBasis,
                Clock.systemUTC());
    }

    /**
     * Retries only a calculator or an AI that may come back, blocking the partition so a product's updates keep their order; any other
     * failure is not retried, and a record that fails for good goes to review. Spring Kafka picks the retry on a record's first
     * failure and again only when the failure's class changes, so a transient failure must be a type of its own.
     */
    @Bean
    DefaultErrorHandler errorHandler(
            @Value("${classification.retry.initial-interval}") Duration initialInterval,
            @Value("${classification.retry.multiplier}") double multiplier,
            @Value("${classification.retry.max-interval}") Duration maxInterval,
            @Value("${classification.retry.max-retries}") int maxRetries,
            KafkaTemplate<String, Object> kafka) {
        var backOff = new ExponentialBackOff(initialInterval.toMillis(), multiplier);
        backOff.setMaxInterval(maxInterval.toMillis());
        backOff.setMaxAttempts(maxRetries);
        var handler = new DefaultErrorHandler(new ReviewRecoverer(kafka), backOff);
        handler.defaultFalse();
        handler.addRetryableExceptions(CalculatorUnavailable.class, AiUnavailable.class);
        return handler;
    }
}
