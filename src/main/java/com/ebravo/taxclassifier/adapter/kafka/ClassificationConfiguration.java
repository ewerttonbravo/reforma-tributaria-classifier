package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.application.AiAdvisor;
import com.ebravo.taxclassifier.application.ClassifyProduct;
import com.ebravo.taxclassifier.application.OfficialCandidates;
import com.ebravo.taxclassifier.application.OfficialLegalBasis;
import com.ebravo.taxclassifier.domain.SuggestionCheck;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClassificationConfiguration {

    @Bean
    ClassifyProduct classifyProduct(OfficialCandidates candidates, AiAdvisor advisor, OfficialLegalBasis legalBasis,
            @Value("${classification.confidence-threshold}") BigDecimal threshold) {
        return new ClassifyProduct(candidates, advisor, new SuggestionCheck(threshold), legalBasis, Clock.systemUTC());
    }
}
