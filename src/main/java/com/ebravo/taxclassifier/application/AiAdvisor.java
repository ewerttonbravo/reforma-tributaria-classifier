package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxSituation;
import java.util.List;

public interface AiAdvisor {

    /** The CST and cClassTrib the AI chooses for the product from the official list; throws if no suggestion can be read. */
    Suggestion suggest(Product product, List<TaxSituation> official);
}
