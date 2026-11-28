package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.adapter.kafka.ClassificationListener.ReviewMessage;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ErrorHandlingUtils;
import org.springframework.kafka.support.KafkaUtils;

/**
 * Sends a record whose classification failed for good, or whose retries ran out, to review with the failure's cause, so no
 * product is left without a result. A record that is not a product's JSON goes with no product.
 */
final class ReviewRecoverer implements ConsumerRecordRecoverer {

    static final String FAILED = "FAILED";

    private static final LogAccessor LOG = new LogAccessor(ReviewRecoverer.class);

    private final KafkaTemplate<String, Object> kafka;

    ReviewRecoverer(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        LOG.error(exception, () -> "Sending to review after its classification failed: " + KafkaUtils.format(record));
        var product = record.value() instanceof ProductMessage message ? message : null;
        var cause = ErrorHandlingUtils.findRootCause(exception).getMessage();
        // Waits for the send: if it fails, the error handler reads the record again instead of committing it with no result.
        kafka.send(ClassificationListener.REVIEW_TOPIC, (String) record.key(),
                new ReviewMessage(product, null, FAILED, List.of(), cause)).join();
    }
}
