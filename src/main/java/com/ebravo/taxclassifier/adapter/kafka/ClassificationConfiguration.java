package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.application.AiAdvisor;
import com.ebravo.taxclassifier.application.ClassifyProduct;
import com.ebravo.taxclassifier.application.OfficialCandidates;
import com.ebravo.taxclassifier.application.OfficialLegalBasis;
import com.ebravo.taxclassifier.application.SuggestionStore;
import com.ebravo.taxclassifier.application.TaxCalculator;
import com.ebravo.taxclassifier.domain.SuggestionCheck;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class ClassificationConfiguration {

    @Bean
    ClassifyProduct classifyProduct(OfficialCandidates candidates, AiAdvisor advisor, SuggestionStore suggestions,
            TaxCalculator calculator, OfficialLegalBasis legalBasis,
            @Value("${classification.confidence-threshold}") BigDecimal threshold) {
        return new ClassifyProduct(candidates, advisor, suggestions, new SuggestionCheck(threshold), calculator, legalBasis,
                Clock.systemUTC());
    }

    /** No retry until task 12b: Spring's default tries a failed record 10 times, each one a paid call to the AI. */
    @Bean
    DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(0, 0));
    }
}
