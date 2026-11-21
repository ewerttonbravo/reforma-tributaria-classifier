package com.ebravo.taxclassifier.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class ProductCsv {

    public static ProductCsvResult parse(List<String> lines) {
        var products = new ArrayList<Product>();
        for (var line : lines.subList(1, lines.size())) {
            var fields = line.split(";", -1);
            var price = new BigDecimal(fields[3]).setScale(2, RoundingMode.UNNECESSARY);
            products.add(new Product(fields[0], fields[1], fields[2], price));
        }
        return new ProductCsvResult(List.copyOf(products), List.of());
    }

    private ProductCsv() {}
}
