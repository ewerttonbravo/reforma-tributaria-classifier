package com.ebravo.taxclassifier.adapter.jdbc;

import com.ebravo.taxclassifier.application.SuggestionStore;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSuggestionStore implements SuggestionStore {

    @Override
    public Optional<Suggestion> find(Product product) {
        return Optional.empty();
    }

    @Override
    public void save(Product product, Suggestion suggestion) {}
}
