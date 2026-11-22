package com.ebravo.taxclassifier.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxSituationTest {

    @Test
    void officialListDoesNotChangeWithTheListItWasBuiltFrom() {
        var source = new ArrayList<>(List.of(new TaxClassification("000001", "Situações tributadas integralmente pelo IBS e CBS.")));
        var situation = new TaxSituation("000", "Tributação integral", source);

        source.add(new TaxClassification("999999", "Not official"));

        assertThat(situation.classifications())
                .containsExactly(new TaxClassification("000001", "Situações tributadas integralmente pelo IBS e CBS."));
    }
}
