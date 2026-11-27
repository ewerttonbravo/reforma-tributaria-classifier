package com.ebravo.taxclassifier.adapter.jdbc;

import com.ebravo.taxclassifier.application.SuggestionStore;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSuggestionStore implements SuggestionStore {

    private final JdbcClient jdbc;

    JdbcSuggestionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Suggestion> find(Product product) {
        return jdbc.sql("""
                        SELECT cst, c_class_trib, confidence, justification FROM ai_suggestions
                        WHERE product_code = ? AND input_hash = ?""")
                .params(product.code(), inputHash(product))
                .query((row, n) -> new Suggestion(row.getString("cst"), row.getString("c_class_trib"),
                        row.getBigDecimal("confidence"), row.getString("justification")))
                .optional();
    }

    /** Two instances classifying the same product at once both pay for the AI; the first answer saved stays. */
    @Override
    public void save(Product product, Suggestion suggestion) {
        jdbc.sql("""
                        INSERT INTO ai_suggestions (product_code, input_hash, cst, c_class_trib, confidence, justification)
                        VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""")
                .params(product.code(), inputHash(product), suggestion.cst(), suggestion.cClassTrib(),
                        suggestion.confidence(), suggestion.justification())
                .update();
    }

    // The NCM's length goes first, so no NCM and description can pass for another pair, whatever reaches produtos.
    private static String inputHash(Product product) {
        var ncm = Objects.requireNonNull(product.ncm(), "ncm");
        var description = Objects.requireNonNull(product.description(), "description");
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest((ncm.length() + ":" + ncm + description).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK has SHA-256", e);
        }
    }
}
