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
        assertThat(domainViolations()).anyMatch(line -> line.contains("<org.springframework.context.ApplicationContext>"));
    }

    @Test
    void domainDependingOnAThirdPartyDomainPackageIsAViolation() {
        assertThat(domainViolations()).anyMatch(line -> line.contains("<com.tngtech.archunit.core.domain.JavaClass>"));
    }

    @Test
    void domainDependingOnHttpIsAViolation() {
        assertThat(domainViolations()).anyMatch(line -> line.contains("<java.net.http.HttpClient>"));
    }

    @Test
    void domainDependingOnTheJdkCoreIsAllowed() {
        assertThat(domainViolations()).noneMatch(line -> line.contains("archfixture.domain.Price"));
    }

    @Test
    void applicationDependingOnAnAdapterIsAViolation() {
        assertThat(applicationViolations()).anyMatch(line -> line.contains("<com.ebravo.taxclassifier.archfixture.adapter.CalculatorClient>"));
    }

    @Test
    void applicationDependingOnTheDomainIsAllowed() {
        assertThat(applicationViolations()).noneMatch(line -> line.contains("PriceProducts"));
    }

    private static List<String> domainViolations() {
        return violations(ArchitectureRules.DOMAIN_DEPENDS_ON_THE_JDK_ONLY);
    }

    private static List<String> applicationViolations() {
        return violations(ArchitectureRules.APPLICATION_DEPENDS_ON_THE_DOMAIN_AND_THE_JDK_ONLY);
    }

    private static List<String> violations(ArchRule rule) {
        return rule.evaluate(FIXTURES).getFailureReport().getDetails();
    }
}
