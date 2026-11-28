package com.ebravo.taxclassifier.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebravo.taxclassifier.application.AiFailure;
import com.ebravo.taxclassifier.application.AiFailure.Kind;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.mock.MockProducerFactory;

class ReviewRecovererTest {

    private static final ConsumerRecord<String, Object> REFUSED_PRODUCT = new ConsumerRecord<>("produtos", 0, 42L, "P003",
            new ProductMessage("P003", "Produto recusado", "10063021", "1.00"));
    private static final AiFailure REFUSAL = new AiFailure(Kind.REFUSED, "the AI refused", null);

    private final MockProducer<String, Object> producer =
            new MockProducer<>(true, null, new StringSerializer(), (topic, value) -> new byte[0]);
    private final ReviewRecoverer recoverer = new ReviewRecoverer(new KafkaTemplate<>(new MockProducerFactory<>(() -> producer)), 3);

    @Test
    void reviewThatCannotBeSentIsGivenUpAfterTheConfiguredAttempts() {
        producer.sendException = new KafkaException("the broker is down");

        // Each failed attempt throws, so the error handler reads the record again; the last one gives up instead.
        assertThatThrownBy(() -> recoverer.accept(REFUSED_PRODUCT, REFUSAL)).hasStackTraceContaining("the broker is down");
        assertThatThrownBy(() -> recoverer.accept(REFUSED_PRODUCT, REFUSAL)).hasStackTraceContaining("the broker is down");
        assertThatCode(() -> recoverer.accept(REFUSED_PRODUCT, REFUSAL)).doesNotThrowAnyException();
    }

    @Test
    void reviewSentStartsTheCountOver() {
        producer.sendException = new KafkaException("the broker is down");
        assertThatThrownBy(() -> recoverer.accept(REFUSED_PRODUCT, REFUSAL)).isNotNull();
        producer.sendException = null;
        recoverer.accept(REFUSED_PRODUCT, REFUSAL);

        // Read again later, the record has its 3 attempts again.
        producer.sendException = new KafkaException("the broker is down");
        assertThatThrownBy(() -> recoverer.accept(REFUSED_PRODUCT, REFUSAL)).isNotNull();
        assertThatThrownBy(() -> recoverer.accept(REFUSED_PRODUCT, REFUSAL)).isNotNull();
    }
}
