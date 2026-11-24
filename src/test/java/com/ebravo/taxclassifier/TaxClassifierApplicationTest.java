package com.ebravo.taxclassifier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaxClassifierApplicationTest {

    @Test
    void publishCommandRunsInThePublishProfile() {
        // The classification listener is left out of the publish profile, so publishing never calls the AI.
        assertThat(TaxClassifierApplication.application("--spring.main.banner-mode=off", "publish", "products.csv")
                .getAdditionalProfiles()).containsExactly("publish");
        assertThat(TaxClassifierApplication.application().getAdditionalProfiles()).isEmpty();
    }
}
