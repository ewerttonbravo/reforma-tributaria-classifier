package com.ebravo.taxclassifier;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.ebravo.taxclassifier", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDependsOnTheJdkOnly =
            ArchitectureRules.DOMAIN_DEPENDS_ON_THE_JDK_ONLY;

    @ArchTest
    static final ArchRule applicationDependsOnTheDomainAndTheJdkOnly =
            ArchitectureRules.APPLICATION_DEPENDS_ON_THE_DOMAIN_AND_THE_JDK_ONLY;
}
