package com.ebravo.taxclassifier.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ebravo.taxclassifier.application.AiAdvisor;
import com.ebravo.taxclassifier.application.AiFailure;
import com.ebravo.taxclassifier.application.AiFailure.Kind;
import com.ebravo.taxclassifier.application.AiUnavailable;
import com.ebravo.taxclassifier.application.CalculatorUnavailable;
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
import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

// The threshold is above the default, so a review at 0.85 proves it is read from configuration. The retries keep their configured
// count, with short waits.
@SpringBootTest(properties = {
    "classification.confidence-threshold=0.90",
    "classification.retry.initial-interval=10ms",
    "classification.retry.max-interval=10ms"
})
@Testcontainers
class ClassificationListenerTest {

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.2");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.7");

    private static final List<TaxSituation> OFFICIAL = List.of(
            new TaxSituation("200", "Alíquota reduzida", List.of(
                    new TaxClassification("200003", "Vendas de produtos destinados à alimentação humana (Anexo I)"),
                    new TaxClassification("200035", "Fornecimento dos produtos de higiene pessoal e limpeza (Anexo VIII)"))),
            new TaxSituation("220", "Alíquota fixa", List.of(
                    new TaxClassification("220001", "Incorporação imobiliária submetida ao regime especial de tributação"))));
    private static final LegalBasis BASIS_OF_200003 = new LegalBasis("Art. 125", "Art. 125. Ficam reduzidas a zero", "LC 214/2025");
    private static final Suggestion RICE = new Suggestion("200", "200003", new BigDecimal("0.95"), "Arroz integra a cesta básica.");
    /** The fake AI's answer, by product code. */
    private static final Map<String, Suggestion> ANSWERS = Map.ofEntries(
            Map.entry("P001", RICE),
            Map.entry("P002", new Suggestion("200", "200003", new BigDecimal("0.85"), "Feijão parece integrar a cesta básica.")),
            Map.entry("P004", RICE),
            Map.entry("P005", new Suggestion("220", "220001", new BigDecimal("0.95"), "Incorporação imobiliária em regime especial.")),
            Map.entry("P007", RICE),
            Map.entry("P008", new Suggestion("200", "200035", new BigDecimal("0.95"), "Arroz parece ter alíquota reduzida.")),
            Map.entry("P009", RICE),
            Map.entry("P010", RICE),
            Map.entry("P011", RICE),
            Map.entry("P012", RICE),
            Map.entry("P013", RICE),
            Map.entry("P014", RICE),
            Map.entry("P015", RICE),
            Map.entry("P017", RICE));
    /** REG-011 as release 1.6.1 answered it for P001 under 200035. */
    private static final CalculationError NOT_LINKED = new CalculationError("REG-011",
            "NCM de código 10063021 não vinculada à Classificação Tributária de código 200035 (CBS e IBS)");
    /** The codes the fake AI was asked about; P015 and P016 find it unavailable the first time, and a code without an answer makes it fail. */
    private static final List<String> ADVISED = new CopyOnWriteArrayList<>();
    /** Whether the service already died once while calculating P009, after the AI answered and before publishing. */
    private static final AtomicBoolean DIED = new AtomicBoolean();
    /** The codes the fake calculator was asked to calculate. */
    private static final List<String> CALCULATED = new CopyOnWriteArrayList<>();

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
                 "reason":"LOW_CONFIDENCE","errors":[],"cause":null}""",
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
                 "errors":[{"code":"REG-011","detail":"%s"}],"cause":null}""".formatted(NOT_LINKED.detail()),
                recordWithKey("produtos-revisao", "P008").value(), JSONCompareMode.STRICT);
    }

    @Test
    void permanentFailureGoesToReviewWithItsCause() throws Exception {
        // One partition, so P004 is classified only after the listener is done with P003.
        kafka.send(KafkaProductPublisher.TOPIC, 0, "P003", new ProductMessage("P003", "Produto sem resposta", "10063021", "1.00"));
        kafka.send(KafkaProductPublisher.TOPIC, 0, "P004", new ProductMessage("P004", "Arroz parboilizado 1kg", "10063021", "6.50"))
                .join();

        JSONAssert.assertEquals(
                """
                {"product":{"code":"P003","description":"Produto sem resposta","ncm":"10063021","price":"1.00"},
                 "suggestion":null,"reason":"FAILED","errors":[],"cause":"the AI refused"}""",
                recordWithKey("produtos-revisao", "P003").value(), JSONCompareMode.STRICT);
        assertThat(recordWithKey("produtos-classificados", "P004")).isNotNull();
        assertThat(ADVISED).filteredOn("P003"::equals).hasSize(1);
    }

    @Test
    void malformedRecordGoesToReviewWithNoProduct() throws Exception {
        // Not a product's JSON, so it fails while the consumer polls, before the listener sees it.
        var config = Map.<String, Object>of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (var producer = new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(KafkaProductPublisher.TOPIC, 1, "P006", "oops")).get();
        }
        kafka.send(KafkaProductPublisher.TOPIC, 1, "P007", new ProductMessage("P007", "Arroz integral 1kg", "10063021", "9.90"))
                .join();

        JSONAssert.assertEquals(
                """
                {"product":null,"suggestion":null,"reason":"FAILED","errors":[],"cause":"failed to deserialize"}""",
                recordWithKey("produtos-revisao", "P006").value(), JSONCompareMode.STRICT);
        assertThat(recordWithKey("produtos-classificados", "P007")).isNotNull();
    }

    @Test
    void productWithoutNcmGoesToReview() throws Exception {
        kafka.send(KafkaProductPublisher.TOPIC, 1, "P018", new ProductMessage("P018", "Arroz sem NCM", null, "5.00")).join();

        JSONAssert.assertEquals(
                """
                {"product":{"code":"P018","description":"Arroz sem NCM","ncm":null,"price":"5.00"},
                 "suggestion":null,"reason":"FAILED","errors":[],"cause":"the product has no ncm"}""",
                recordWithKey("produtos-revisao", "P018").value(), JSONCompareMode.STRICT);
        assertThat(ADVISED).doesNotContain("P018");
    }

    @Test
    void productIsClassifiedAgainWhenTheServiceDiesBeforePublishing() {
        kafka.send(KafkaProductPublisher.TOPIC, 2, "P009", new ProductMessage("P009", "Arroz agulhinha 1kg", "10063021", "7.20"))
                .join();

        try {
            await().atMost(Duration.ofSeconds(30))
                    .until(() -> registry.getListenerContainers().stream().noneMatch(MessageListenerContainer::isRunning));
        } finally {
            // The other tests share the container; a stop that times out here must not fail them too.
            registry.start();
        }

        // The service died after the AI answered, so the saved suggestion is reused.
        assertThat(recordWithKey("produtos-classificados", "P009")).isNotNull();
        assertThat(ADVISED).filteredOn("P009"::equals).hasSize(1);
    }

    @Test
    void repeatedProductDoesNotCallTheAiAgain() {
        var rice = new Product("P011", "Arroz branco 1kg", "10063021", new BigDecimal("3.00"));
        var newPrice = new Product("P011", "Arroz branco 1kg", "10063021", new BigDecimal("4.00"));

        publisher.publish(List.of(rice, rice, newPrice));

        // One partition per key, so the new price is classified after both copies, and calculated on its own price.
        assertThat(recordWithKey("produtos-classificados", "P011", value -> value.contains("\"base\":\"4.00\""))).isNotNull();
        assertThat(ADVISED).filteredOn("P011"::equals).hasSize(1);
    }

    @Test
    void unavailableCalculatorIsRetriedUntilItComesBack() {
        kafka.send(KafkaProductPublisher.TOPIC, 2, "P012", new ProductMessage("P012", "Arroz cateto 1kg", "10063021", "8.10"))
                .join();

        assertThat(recordWithKey("produtos-classificados", "P012")).isNotNull();
        assertThat(CALCULATED).filteredOn("P012"::equals).hasSize(3);
        // The suggestion was saved before the calculation failed, so the retries do not pay for the AI again.
        assertThat(ADVISED).filteredOn("P012"::equals).hasSize(1);
    }

    @Test
    void exhaustedRetryGoesToReview() throws Exception {
        // One partition, so P014 is classified only after the listener gives up on P013.
        kafka.send(KafkaProductPublisher.TOPIC, 1, "P013", new ProductMessage("P013", "Arroz vermelho 1kg", "10063021", "11.00"));
        kafka.send(KafkaProductPublisher.TOPIC, 1, "P014", new ProductMessage("P014", "Arroz negro 1kg", "10063021", "13.00"))
                .join();

        JSONAssert.assertEquals(
                """
                {"product":{"code":"P013","description":"Arroz vermelho 1kg","ncm":"10063021","price":"11.00"},
                 "suggestion":null,"reason":"FAILED","errors":[],
                 "cause":"the calculator is unavailable: Connection refused"}""",
                recordWithKey("produtos-revisao", "P013").value(), JSONCompareMode.STRICT);
        assertThat(recordWithKey("produtos-classificados", "P014")).isNotNull();
        assertThat(CALCULATED).filteredOn("P013"::equals).hasSize(7);
    }

    @Test
    void unavailableAiIsRetried() {
        kafka.send(KafkaProductPublisher.TOPIC, 2, "P015", new ProductMessage("P015", "Arroz japonês 1kg", "10063021", "15.00"))
                .join();

        assertThat(recordWithKey("produtos-classificados", "P015")).isNotNull();
        assertThat(ADVISED).filteredOn("P015"::equals).hasSize(2);
    }

    @Test
    void retryStopsWhenTheFailureBecomesPermanent() {
        // P016 finds the AI unavailable, then refused; one partition, so P017 is classified only after P016 is given up.
        kafka.send(KafkaProductPublisher.TOPIC, 0, "P016", new ProductMessage("P016", "Produto recusado", "10063021", "1.00"));
        kafka.send(KafkaProductPublisher.TOPIC, 0, "P017", new ProductMessage("P017", "Arroz jasmim 1kg", "10063021", "14.00"))
                .join();

        assertThat(recordWithKey("produtos-classificados", "P017")).isNotNull();
        assertThat(ADVISED).filteredOn("P016"::equals).hasSize(2);
    }

    @Test
    void offsetMovesPastAPublishedProduct() throws Exception {
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

    private static ConsumerRecord<String, String> recordWithKey(String topic, String key) {
        return recordWithKey(topic, key, value -> true);
    }

    /** Reads the topic from the beginning until a record with the key and a matching value shows up. */
    private static ConsumerRecord<String, String> recordWithKey(String topic, String key, Predicate<String> value) {
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
                    if (key.equals(record.key()) && value.test(record.value())) {
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
                var asked = ADVISED.stream().filter(product.code()::equals).count();
                if ((product.code().equals("P015") || product.code().equals("P016")) && asked == 1) {
                    throw new AiUnavailable("the AI did not answer", null);
                }
                var answer = ANSWERS.get(product.code());
                if (answer == null) {
                    throw new AiFailure(Kind.REFUSED, "the AI refused", null);
                }
                return answer;
            };
        }

        /**
         * Dies the first time it calculates P009; is unavailable the first 2 times it calculates P012 and every time for P013; REG-011
         * for 200035; otherwise the amounts of the recorded calculation of P001 in 2026, on each product's price as the base,
         * simulated for P005 only, so the flag is proven to reach the topic both ways.
         */
        @Bean
        @Primary
        TaxCalculator fakeCalculator() {
            return (product, suggestion, taxableEvent) -> {
                CALCULATED.add(product.code());
                if (product.code().equals("P009") && DIED.compareAndSet(false, true)) {
                    throw new ServiceDied();
                }
                var attempts = CALCULATED.stream().filter(product.code()::equals).count();
                if (product.code().equals("P012") && attempts <= 2 || product.code().equals("P013")) {
                    throw new CalculatorUnavailable(new ConnectException("Connection refused"));
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
