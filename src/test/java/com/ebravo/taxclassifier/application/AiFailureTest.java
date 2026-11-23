package com.ebravo.taxclassifier.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebravo.taxclassifier.application.AiFailure.Kind;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

    /** An exception may cross a process boundary by Java serialization; the status must survive it. */
    @Test
    void failureSurvivesSerialization() throws IOException, ClassNotFoundException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(new AiFailure(429, null));
        }

        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var copy = (AiFailure) in.readObject();
            assertThat(copy.kind()).isEqualTo(Kind.HTTP_ERROR);
            assertThat(copy.status()).hasValue(429);
        }
    }
}
