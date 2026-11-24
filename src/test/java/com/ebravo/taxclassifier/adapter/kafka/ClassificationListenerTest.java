package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.application.AiAdvisor;
import com.ebravo.taxclassifier.application.OfficialCandidates;
import com.ebravo.taxclassifier.application.OfficialLegalBasis;
import com.ebravo.taxclassifier.application.ProductPublisher;
import com.ebravo.taxclassifier.domain.LegalBasis;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxClassification;
import com.ebravo.taxclassifier.domain.TaxSituation;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

// The threshold is above the default, so a review at 0.85 proves it is read from configuration.
@SpringBootTest(properties = "classification.confidence-threshold=0.90")
@Testcontainers
class ClassificationListenerTest {

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.2");

    private static final List<TaxSituation> OFFICIAL = List.of(new TaxSituation("200", "Alíquota reduzida", List.of(
            new TaxClassification("200003", "Vendas de produtos destinados à alimentação humana (Anexo I)"))));
    private static final LegalBasis BASIS_OF_200003 = new LegalBasis("Art. 125", "Art. 125. Ficam reduzidas a zero", "LC 214/2025");
    /** The fake AI's answer, by product code. */
    private static final Map<String, Suggestion> ANSWERS = Map.of(
            "P001", new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica."),
            "P002", new Suggestion("200", "200003", new BigDecimal("0.85"), "Feijão parece integrar a cesta básica."));

    @Autowired
    ProductPublisher publisher;

    @Test
    void acceptedProductIsPublishedAsClassifiedWithItsLegalBasis() throws Exception {
        publisher.publish(List.of(new Product("P001", "Arroz tipo 1 5kg", "10063021", new BigDecimal("25.90"))));

        JSONAssert.assertEquals(
                """
                {"product":{"code":"P001","description":"Arroz tipo 1 5kg","ncm":"10063021","price":"25.90"},
                 "suggestion":{"cst":"200","cClassTrib":"200003","confidence":"0.95","justification":"Arroz integra a cesta básica."},
                 "legalBasis":{"shortText":"Art. 125","text":"Art. 125. Ficam reduzidas a zero","normativeReference":"LC 214/2025"}}""",
                recordWithKey("produtos-classificados", "P001").value(), JSONCompareMode.STRICT);
    }

    @Test
    void rejectedProductGoesToReviewWithItsReason() throws Exception {
        publisher.publish(List.of(new Product("P002", "Feijão carioca 1kg", "07133319", new BigDecimal("8.49"))));

        JSONAssert.assertEquals(
                """
                {"product":{"code":"P002","description":"Feijão carioca 1kg","ncm":"07133319","price":"8.49"},
                 "suggestion":{"cst":"200","cClassTrib":"200003","confidence":"0.85","justification":"Feijão parece integrar a cesta básica."},
                 "reason":"LOW_CONFIDENCE"}""",
                recordWithKey("produtos-revisao", "P002").value(), JSONCompareMode.STRICT);
    }

    /** Reads the topic from the beginning until a record with the key shows up. */
    private static ConsumerRecord<String, String> recordWithKey(String topic, String key) {
        var config = Map.<String, Object>of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            var partitions = consumer.partitionsFor(topic).stream()
                    .map(info -> new TopicPartition(info.topic(), info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            var deadline = Instant.now().plusSeconds(30);
            while (Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
            throw new AssertionError("no record with key " + key + " in " + topic);
        }
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        OfficialCandidates fakeCandidates() {
            return date -> OFFICIAL;
        }

        @Bean
        @Primary
        AiAdvisor fakeAdvisor() {
            return (product, official) -> ANSWERS.get(product.code());
        }

        @Bean
        @Primary
        OfficialLegalBasis fakeLegalBasis() {
            return (cClassTrib, date) -> cClassTrib.equals("200003") ? Optional.of(BASIS_OF_200003) : Optional.empty();
        }
    }
}
