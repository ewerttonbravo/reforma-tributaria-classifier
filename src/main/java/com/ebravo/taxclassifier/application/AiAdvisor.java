package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxSituation;
import java.util.List;

public interface AiAdvisor {

    /**
     * The CST and cClassTrib the AI chooses for the product from the official list.
     *
     * @throws AiFailure if the AI refuses, its answer is cut short or malformed, or the call fails with an HTTP error other
     *     than 408, 409, 429 or 5xx
     * @throws AiUnavailable if the call gets no answer (no connection, timeout), a 408, 409, 429 or a 5xx
     */
    Suggestion suggest(Product product, List<TaxSituation> official);
}
