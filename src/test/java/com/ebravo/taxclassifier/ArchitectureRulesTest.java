package com.ebravo.taxclassifier;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves each rule catches a violation, since the production packages start out empty. */
class ArchitectureRulesTest {

    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages("com.ebravo.taxclassifier.archfixture");

    @Test
    void domainDependingOnSpringIsAViolation() {
        assertThat(violations(ArchitectureRules.DOMAIN_DEPENDS_ON_THE_JDK_ONLY))
                .isNotEmpty()
                .allMatch(line -> line.contains("SpringAwareProduct"))
                .anyMatch(line -> line.contains("org.springframework.context.ApplicationContext"));
    }

    @Test
    void applicationDependingOnAnAdapterIsAViolation() {
        assertThat(violations(ArchitectureRules.APPLICATION_DEPENDS_ON_THE_DOMAIN_AND_THE_JDK_ONLY))
                .isNotEmpty()
                .allMatch(line -> line.contains("ClassifyProduct"))
                .anyMatch(line -> line.contains("archfixture.adapter.CalculatorClient"));
    }

    private static List<String> violations(ArchRule rule) {
        return rule.evaluate(FIXTURES).getFailureReport().getDetails();
    }
}
