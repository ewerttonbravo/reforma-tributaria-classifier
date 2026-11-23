package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxSituation;
import java.util.List;

public interface AiAdvisor {

    /**
     * The CST and cClassTrib the AI chooses for the product from the official list.
     *
     * @throws AiFailure if the AI refuses, its answer is cut short or malformed, or the call fails with an HTTP error;
     *     a call that gets no answer at all (no connection, timeout) is not an {@code AiFailure} until task 12
     */
    Suggestion suggest(Product product, List<TaxSituation> official);
}
