package com.ebravo.taxclassifier.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebravo.taxclassifier.application.ProductPublisher;
import com.ebravo.taxclassifier.domain.Product;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
@Testcontainers
class KafkaProductPublisherTest {

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.2");

    @Autowired
    ProductPublisher publisher;

    @Test
    void eachProductIsKeyedByItsCodeWithAJsonValue() throws Exception {
        var records = publishAndRead(List.of(new Product("P001", "Arroz tipo 1 5kg", "10063021", new BigDecimal("25.90"))));

        var record = records.getFirst();
        assertThat(records).hasSize(1);
        assertThat(record.key()).isEqualTo("P001");
        JSONAssert.assertEquals(
                """
                {"code":"P001","description":"Arroz tipo 1 5kg","ncm":"10063021","price":"25.90"}""",
                record.value(), JSONCompareMode.STRICT);
        assertThat(record.headers()).isEmpty();
    }

    /** Reads only what this call publishes: the consumer starts at the end of every partition. */
    private List<ConsumerRecord<String, String>> publishAndRead(List<Product> products) {
        var config = Map.<String, Object>of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            var partitions = consumer.partitionsFor(KafkaProductPublisher.TOPIC).stream()
                    .map(info -> new TopicPartition(info.topic(), info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToEnd(partitions);
            partitions.forEach(consumer::position);

            publisher.publish(products);

            var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10), products.size());
            return StreamSupport.stream(records.spliterator(), false).toList();
        }
    }
}
