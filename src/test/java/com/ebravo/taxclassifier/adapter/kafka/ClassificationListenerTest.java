package com.ebravo.taxclassifier.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ebravo.taxclassifier.application.AiAdvisor;
import com.ebravo.taxclassifier.application.AiFailure;
import com.ebravo.taxclassifier.application.AiFailure.Kind;
import com.ebravo.taxclassifier.application.OfficialCandidates;
import com.ebravo.taxclassifier.application.OfficialLegalBasis;
import com.ebravo.taxclassifier.application.ProductPublisher;
import com.ebravo.taxclassifier.application.TaxCalculator;
import com.ebravo.taxclassifier.domain.CalculationError;
import com.ebravo.taxclassifier.domain.LegalBasis;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxCalculation.Calculated;
import com.ebravo.taxclassifier.domain.TaxCalculation.InputInconsistency;
import com.ebravo.taxclassifier.domain.TaxClassification;
import com.ebravo.taxclassifier.domain.TaxSituation;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.MessageListenerContainer;
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

    private static final List<TaxSituation> OFFICIAL = List.of(
            new TaxSituation("200", "Alíquota reduzida", List.of(
                    new TaxClassification("200003", "Vendas de produtos destinados à alimentação humana (Anexo I)"),
                    new TaxClassification("200035", "Fornecimento dos produtos de higiene pessoal e limpeza (Anexo VIII)"))),
            new TaxSituation("220", "Alíquota fixa", List.of(
                    new TaxClassification("220001", "Incorporação imobiliária submetida ao regime especial de tributação"))));
    private static final LegalBasis BASIS_OF_200003 = new LegalBasis("Art. 125", "Art. 125. Ficam reduzidas a zero", "LC 214/2025");
    /** The fake AI's answer, by product code. */
    private static final Map<String, Suggestion> ANSWERS = Map.of(
            "P001", new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica."),
            "P002", new Suggestion("200", "200003", new BigDecimal("0.85"), "Feijão parece integrar a cesta básica."),
            "P004", new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica."),
            "P005", new Suggestion("220", "220001", new BigDecimal("0.95"), "Incorporação imobiliária em regime especial."),
            "P007", new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica."),
            "P008", new Suggestion("200", "200035", new BigDecimal("0.95"), "Arroz parece ter alíquota reduzida."),
            "P009", new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica."),
            "P010", new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica."));
    /** REG-011 as release 1.6.1 answered it for P001 under 200035. */
    private static final CalculationError NOT_LINKED = new CalculationError("REG-011",
            "NCM de código 10063021 não vinculada à Classificação Tributária de código 200035 (CBS e IBS)");
    /** The codes the fake AI was asked about; a code without an answer makes it fail. */
    private static final List<String> ADVISED = new CopyOnWriteArrayList<>();
    /** Whether the service already died once while calculating P009, after the AI answered and before publishing. */
    private static final AtomicBoolean DIED = new AtomicBoolean();

    @Autowired
    ProductPublisher publisher;

    @Autowired
    KafkaTemplate<String, ProductMessage> kafka;

    @Autowired
    ConsumerFactory<?, ?> consumerFactory;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Test
    void acceptedProductIsPublishedAsClassifiedWithItsLegalBasis() throws Exception {
        publisher.publish(List.of(new Product("P001", "Arroz tipo 1 5kg", "10063021", new BigDecimal("25.90"))));

        JSONAssert.assertEquals(
                """
                {"product":{"code":"P001","description":"Arroz tipo 1 5kg","ncm":"10063021","price":"25.90"},
                 "suggestion":{"cst":"200","cClassTrib":"200003","confidence":"0.95","justification":"Arroz integra a cesta básica."},
                 "legalBasis":{"shortText":"Art. 125","text":"Art. 125. Ficam reduzidas a zero","normativeReference":"LC 214/2025"},
                 "calculation":{"base":"25.90","cbs":"0.23","ibs":"0.03","simulated":false}}""",
                recordWithKey("produtos-classificados", "P001").value(), JSONCompareMode.STRICT);
    }

    @Test
    void classificationWithNoPublishedLegalBasisCarriesANullOne() throws Exception {
        publisher.publish(List.of(new Product("P005", "Unidade imobiliária", "10063021", new BigDecimal("1000.00"))));

        JSONAssert.assertEquals(
                """
                {"product":{"code":"P005","description":"Unidade imobiliária","ncm":"10063021","price":"1000.00"},
                 "suggestion":{"cst":"220","cClassTrib":"220001","confidence":"0.95","justification":"Incorporação imobiliária em regime especial."},
                 "legalBasis":null,
                 "calculation":{"base":"1000.00","cbs":"0.23","ibs":"0.03","simulated":true}}""",
                recordWithKey("produtos-classificados", "P005").value(), JSONCompareMode.STRICT);
    }

    @Test
    void rejectedProductGoesToReviewWithItsReason() throws Exception {
        publisher.publish(List.of(new Product("P002", "Feijão carioca 1kg", "07133319", new BigDecimal("8.49"))));

        JSONAssert.assertEquals(
                """
                {"product":{"code":"P002","description":"Feijão carioca 1kg","ncm":"07133319","price":"8.49"},
                 "suggestion":{"cst":"200","cClassTrib":"200003","confidence":"0.85","justification":"Feijão parece integrar a cesta básica."},
                 "reason":"LOW_CONFIDENCE","errors":[]}""",
                recordWithKey("produtos-revisao", "P002").value(), JSONCompareMode.STRICT);
    }

    @Test
    void cClassTribNotLinkedToTheNcmGoesToReviewWithTheCalculatorError() throws Exception {
        publisher.publish(List.of(new Product("P008", "Arroz tipo 1 5kg", "10063021", new BigDecimal("25.90"))));

        JSONAssert.assertEquals(
                """
                {"product":{"code":"P008","description":"Arroz tipo 1 5kg","ncm":"10063021","price":"25.90"},
                 "suggestion":{"cst":"200","cClassTrib":"200035","confidence":"0.95","justification":"Arroz parece ter alíquota reduzida."},
                 "reason":"INPUT_INCONSISTENCY",
                 "errors":[{"code":"REG-011","detail":"%s"}]}""".formatted(NOT_LINKED.detail()),
                recordWithKey("produtos-revisao", "P008").value(), JSONCompareMode.STRICT);
    }

    @Test
    void failedClassificationIsTriedOnceAndSkipped() {
        // One partition, so P004 is classified only after the listener is done with P003.
        kafka.send(KafkaProductPublisher.TOPIC, 0, "P003", new ProductMessage("P003", "Produto sem resposta", "10063021", "1.00"));
        kafka.send(KafkaProductPublisher.TOPIC, 0, "P004", new ProductMessage("P004", "Arroz parboilizado 1kg", "10063021", "6.50"))
                .join();

        assertThat(recordWithKey("produtos-classificados", "P004")).isNotNull();
        assertThat(ADVISED).filteredOn("P003"::equals).hasSize(1);
    }

    @Test
    void malformedRecordIsSkipped() throws Exception {
        // Not a product's JSON, so it fails while the consumer polls, before the listener sees it.
        var config = Map.<String, Object>of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (var producer = new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(KafkaProductPublisher.TOPIC, 1, "P006", "oops")).get();
        }
        kafka.send(KafkaProductPublisher.TOPIC, 1, "P007", new ProductMessage("P007", "Arroz integral 1kg", "10063021", "9.90"))
                .join();

        assertThat(recordWithKey("produtos-classificados", "P007")).isNotNull();
    }

    @Test
    void productIsClassifiedAgainWhenTheServiceDiesBeforePublishing() {
        kafka.send(KafkaProductPublisher.TOPIC, 2, "P009", new ProductMessage("P009", "Arroz agulhinha 1kg", "10063021", "7.20"))
                .join();

        await().atMost(Duration.ofSeconds(30))
                .until(() -> registry.getListenerContainers().stream().noneMatch(MessageListenerContainer::isRunning));
        registry.start();

        assertThat(recordWithKey("produtos-classificados", "P009")).isNotNull();
        assertThat(ADVISED).filteredOn("P009"::equals).hasSize(2);
    }

    @Test
    void offsetIsCommittedOnceTheResultIsPublished() throws Exception {
        var sent = kafka.send(KafkaProductPublisher.TOPIC, 0, "P010", new ProductMessage("P010", "Arroz arbóreo 1kg", "10063021", "12.40"))
                .join();
        var partition = new TopicPartition(KafkaProductPublisher.TOPIC, 0);

        assertThat(recordWithKey("produtos-classificados", "P010")).isNotNull();
        try (var admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                var committed = admin.listConsumerGroupOffsets("tax-classifier").partitionsToOffsetAndMetadata().get().get(partition);
                return committed != null && committed.offset() == sent.getRecordMetadata().offset() + 1;
            });
        }
    }

    @Test
    void consumerPollsOneProductAtATime() {
        // Each product waits on the AI; a poll of 500 could outlast max.poll.interval.ms and be classified again.
        assertThat(consumerFactory.getConfigurationProperties()).containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
    }

    @Test
    void listenerCommitsEachProductOnItsOwn() {
        // A crash then reprocesses only the product in flight, whatever the poll size.
        assertThat(registry.getListenerContainers())
                .singleElement()
                .extracting(container -> container.getContainerProperties().getAckMode())
                .isEqualTo(AckMode.RECORD);
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

    /** An {@link Error} stops the consumer without committing, as the process dying would. */
    static final class ServiceDied extends Error {}

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
            return (product, official) -> {
                ADVISED.add(product.code());
                var answer = ANSWERS.get(product.code());
                if (answer == null) {
                    throw new AiFailure(Kind.REFUSED, "the AI refused", null);
                }
                return answer;
            };
        }

        /**
         * Dies the first time it calculates P009; REG-011 for 200035; otherwise the amounts of the recorded calculation of P001 in 2026, on each product's price as the
         * base, simulated for P005 only, so the flag is proven to reach the topic both ways.
         */
        @Bean
        @Primary
        TaxCalculator fakeCalculator() {
            return (product, suggestion, taxableEvent) -> {
                if (product.code().equals("P009") && DIED.compareAndSet(false, true)) {
                    throw new ServiceDied();
                }
                return suggestion.cClassTrib().equals("200035")
                        ? new InputInconsistency(List.of(NOT_LINKED))
                        : new Calculated(product.price(), new BigDecimal("0.23"), new BigDecimal("0.03"), product.code().equals("P005"));
            };
        }

        @Bean
        @Primary
        OfficialLegalBasis fakeLegalBasis() {
            return (cClassTrib, date) -> cClassTrib.equals("200003") ? Optional.of(BASIS_OF_200003) : Optional.empty();
        }
    }
}
