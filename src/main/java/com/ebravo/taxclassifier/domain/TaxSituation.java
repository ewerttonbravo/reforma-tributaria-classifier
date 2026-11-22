package com.ebravo.taxclassifier.domain;

import java.util.List;

/** An official CST with the cClassTrib that may be used under it. */
public record TaxSituation(String cst, String description, List<TaxClassification> classifications) {}
