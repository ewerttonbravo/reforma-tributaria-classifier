package com.ebravo.taxclassifier.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebravo.taxclassifier.application.AiFailure.Kind;
import org.junit.jupiter.api.Test;

class AiFailureTest {

    @Test
    void httpErrorCarriesItsStatus() {
        var cause = new RuntimeException("rate limited");

        var failure = new AiFailure(429, cause);

        assertThat(failure.kind()).isEqualTo(Kind.HTTP_ERROR);
        assertThat(failure.status()).hasValue(429);
        assertThat(failure).hasMessage("the AI answered with HTTP 429").hasCause(cause);
    }

    @Test
    void otherFailuresCarryNoStatus() {
        var failure = new AiFailure(Kind.REFUSED, "the AI refused to answer", null);

        assertThat(failure.kind()).isEqualTo(Kind.REFUSED);
        assertThat(failure.status()).isEmpty();
    }
}
