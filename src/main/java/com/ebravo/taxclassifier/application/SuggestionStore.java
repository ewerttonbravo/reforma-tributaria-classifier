package com.ebravo.taxclassifier.application;

import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import java.util.Optional;

/** The AI's suggestions already paid for, so a repeated product is not asked again. */
public interface SuggestionStore {

    /** The suggestion saved for a product with the same code, description and NCM; the price does not count, as the AI never sees it. */
    Optional<Suggestion> find(Product product);

    /** Saves the AI's suggestion for the product; a suggestion already saved for it is kept. */
    void save(Product product, Suggestion suggestion);
}
