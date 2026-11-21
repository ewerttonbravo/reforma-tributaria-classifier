package com.ebravo.taxclassifier;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Whitelists, so a library nobody thought to forbid is caught as well. The JDK is limited to its core:
 * I/O, networking and SQL belong to adapters. Patterns are anchored to the base package so that a
 * library's own {@code domain} package does not count as ours; {@code ..} also matches the test fixtures.
 */
final class ArchitectureRules {

    private static final String DOMAIN = "com.ebravo.taxclassifier..domain..";
    private static final String APPLICATION = "com.ebravo.taxclassifier..application..";
    private static final String[] JDK_CORE = {"java.lang..", "java.util..", "java.math..", "java.time.."};

    static final ArchRule DOMAIN_DEPENDS_ON_THE_JDK_ONLY = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage(with(DOMAIN));

    static final ArchRule APPLICATION_DEPENDS_ON_THE_DOMAIN_AND_THE_JDK_ONLY = classes()
            .that().resideInAPackage(APPLICATION)
            .should().onlyDependOnClassesThat().resideInAnyPackage(with(DOMAIN, APPLICATION));

    private static String[] with(String... layers) {
        return Stream.concat(Arrays.stream(JDK_CORE), Arrays.stream(layers)).toArray(String[]::new);
    }

    private ArchitectureRules() {}
}
