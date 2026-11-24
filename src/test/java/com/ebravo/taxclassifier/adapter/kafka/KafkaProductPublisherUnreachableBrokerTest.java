package com.ebravo.taxclassifier.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebravo.taxclassifier.domain.Product;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class KafkaProductPublisherUnreachableBrokerTest {

    @Test
    void anUnreachableBrokerStopsThePublishAtTheFirstFailedSend() {
        // Nothing listens on port 1: each send waits max.block.ms for metadata, then fails.
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 200);
        var kafka = new KafkaTemplate<>(new DefaultKafkaProducerFactory<String, ProductMessage>(
                config, new StringSerializer(), new JacksonJsonSerializer<>()));
        var failedSends = new AtomicInteger();
        kafka.setProducerListener(new ProducerListener<>() {
            @Override
            public void onError(ProducerRecord<String, ProductMessage> record, RecordMetadata metadata, Exception e) {
                failedSends.incrementAndGet();
            }
        });
        var products = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new Product("P00" + i, "Arroz", "10063021", BigDecimal.TEN))
                .toList();

        assertThatThrownBy(() -> new KafkaProductPublisher(kafka).publish(products))
                .hasRootCauseInstanceOf(org.apache.kafka.common.errors.TimeoutException.class);
        assertThat(failedSends).hasValue(1);
        kafka.destroy();
    }
}
