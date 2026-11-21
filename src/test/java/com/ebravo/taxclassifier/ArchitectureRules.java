package com.ebravo.taxclassifier;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.lang.ArchRule;

/** Whitelists, so a library nobody thought to forbid is caught as well. */
final class ArchitectureRules {

    static final ArchRule DOMAIN_DEPENDS_ON_THE_JDK_ONLY = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..");

    static final ArchRule APPLICATION_DEPENDS_ON_THE_DOMAIN_AND_THE_JDK_ONLY = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..", "..application..");

    private ArchitectureRules() {}
}
