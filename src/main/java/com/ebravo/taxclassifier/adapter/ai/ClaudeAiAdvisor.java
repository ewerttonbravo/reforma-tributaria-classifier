package com.ebravo.taxclassifier.adapter.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.ebravo.taxclassifier.application.AiAdvisor;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxSituation;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ClaudeAiAdvisor implements AiAdvisor {

    private static final String MODEL = "claude-haiku-5-5";
    private static final long MAX_TOKENS = 4096;

    private final AnthropicClient claude;

    ClaudeAiAdvisor(@Value("${ai.base-url}") String baseUrl, @Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        // Retries belong to the classification flow, where they can be observed and tested.
        this.claude = AnthropicOkHttpClient.builder().baseUrl(baseUrl).apiKey(apiKey).maxRetries(0).build();
    }

    @Override
    public Suggestion suggest(Product product, List<TaxSituation> official) {
        var params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .outputConfig(Answer.class)
                .addUserMessage(product.description())
                .build();
        return claude.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .orElseThrow()
                .text()
                .toSuggestion();
    }

    /** The JSON the model must answer with. */
    record Answer(String cst, String cClassTrib, String justification, BigDecimal confidence) {

        Suggestion toSuggestion() {
            return new Suggestion(cst, cClassTrib, confidence, justification);
        }
    }
}
