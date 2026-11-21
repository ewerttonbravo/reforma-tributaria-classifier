package com.ebravo.taxclassifier.domain;

import java.util.List;

public record ProductCsvResult(List<Product> products, List<RejectedLine> rejected) {}
