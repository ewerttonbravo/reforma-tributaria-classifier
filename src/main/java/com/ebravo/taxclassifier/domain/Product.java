package com.ebravo.taxclassifier.domain;

import java.math.BigDecimal;

public record Product(String code, String description, String ncm, BigDecimal price) {}
