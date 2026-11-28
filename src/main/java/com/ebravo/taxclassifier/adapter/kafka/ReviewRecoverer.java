package com.ebravo.taxclassifier.adapter.kafka;

import com.ebravo.taxclassifier.adapter.kafka.ClassificationListener.ReviewMessage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
    private final int maxAttempts;
    /** Failed sends by record position; a send that fails makes the error handler read the record again. */
    private final Map<String, Integer> failedSends = new ConcurrentHashMap<>();

    ReviewRecoverer(KafkaTemplate<String, Object> kafka, int maxAttempts) {
        this.kafka = kafka;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        var position = KafkaUtils.format(record);
        LOG.error(exception, () -> "Sending to review after its classification failed: " + position);
        var product = record.value() instanceof ProductMessage message ? message : null;
        var failure = ErrorHandlingUtils.findRootCause(exception);
        // Null only for a review the classification decided, so a failure with no message is named by its type.
        var cause = Objects.requireNonNullElse(failure.getMessage(), failure.getClass().getName());
        try {
            kafka.send(ClassificationListener.REVIEW_TOPIC, (String) record.key(),
                    new ReviewMessage(product, null, FAILED, List.of(), cause)).join();
            failedSends.remove(position);
        } catch (RuntimeException sendFailure) {
            // Each round classifies the record again, which may pay for the AI again, so the rounds are limited.
            int failures = failedSends.merge(position, 1, Integer::sum);
            if (failures < maxAttempts) {
                throw sendFailure;
            }
            failedSends.remove(position);
            LOG.error(sendFailure, () -> "Gave up sending to review after " + failures + " attempts, skipping: " + position);
        }
    }
}
