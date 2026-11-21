package com.ebravo.taxclassifier.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ProductCsv {

    private static final String HEADER = "code;description;ncm;price";
    private static final Pattern NCM = Pattern.compile("\\d{8}");
    private static final Pattern PRICE = Pattern.compile("-?\\d+(\\.\\d+)?");

    public static ProductCsvResult parse(List<String> lines) {
        var products = new ArrayList<Product>();
        var rejected = new ArrayList<RejectedLine>();
        var headerIsRight = !lines.isEmpty() && lines.getFirst().equals(HEADER);
        if (!headerIsRight) {
            rejected.add(new RejectedLine(1, "expected header '" + HEADER + "'"));
        }
        for (int i = 1; i < lines.size(); i++) {
            var lineNumber = i + 1;
            // -1 keeps trailing empty fields, so "a;b;c;d;" counts five fields
            var fields = lines.get(i).split(";", -1);
            var reason = headerIsRight ? reasonToReject(fields) : Optional.of("not read: the header is wrong");
            reason.ifPresentOrElse(
                    why -> rejected.add(new RejectedLine(lineNumber, why)),
                    () -> products.add(toProduct(fields)));
        }
        return new ProductCsvResult(List.copyOf(products), List.copyOf(rejected));
    }

    private static Optional<String> reasonToReject(String[] fields) {
        if (fields.length != 4) {
            return Optional.of("expected 4 fields separated by ';', found " + fields.length);
        }
        if (fields[0].isBlank()) {
            return Optional.of("code is blank");
        }
        if (!fields[0].equals(fields[0].strip())) {
            return Optional.of("code has leading or trailing spaces");
        }
        if (fields[1].isBlank()) {
            return Optional.of("description is blank");
        }
        if (!NCM.matcher(fields[2]).matches()) {
            return Optional.of("NCM must have 8 digits");
        }
        if (!PRICE.matcher(fields[3]).matches()) {
            return Optional.of("price must be a decimal number like 12.50");
        }
        var price = new BigDecimal(fields[3]);
        if (price.scale() > 2) {
            return Optional.of("price has more than 2 decimal places");
        }
        if (price.signum() <= 0) {
            return Optional.of("price must be greater than zero");
        }
        return Optional.empty();
    }

    private static Product toProduct(String[] fields) {
        var price = new BigDecimal(fields[3]).setScale(2, RoundingMode.UNNECESSARY);
        return new Product(fields[0], fields[1], fields[2], price);
    }

    private ProductCsv() {}
}
