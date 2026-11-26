# Failure scenarios

How to reproduce each failure scenario by hand, and how to break the protection that handles it. This document grows with tasks 10 to 15 of the [roadmap](roadmap.md).

Every section assumes Kafka running (`docker compose up -d`), the official calculator on `:8080` and the service built (`./mvnw -DskipTests package`), as in the [README](../README.md).

## Crash before publishing (task 10)

The service commits a product's offset only after its result is sent (D20). A product in flight when the service dies is classified again when it comes back.

### Reproduce
0. Start from an empty broker, so the topics and the group's offsets hold this run only: `docker compose down && docker compose up -d` (the broker has no volume, so this deletes every topic).
1. Start the service with a Claude API key and no other arguments: `java -jar target/tax-classifier.jar`.
2. In another terminal, publish the sample: `java -jar target/tax-classifier.jar publish samples/products.csv`.
3. A few seconds later, before the 11 products are done (each one waits on the AI), kill the service without a shutdown: `kill -9 $(pgrep -f 'tax-classifier.jar$')`.
4. Compare what the group committed with what it published:
   ```sh
   docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --describe --group tax-classifier
   for topic in produtos-classificados produtos-revisao; do
     docker compose exec kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic $topic
   done
   ```
   The sum of `CURRENT-OFFSET` over the partitions of `produtos` is the number of products committed; the offsets of the two output topics add up to the results published. Committed is never above published: no product was committed without its result. This holds only if the service logged no failed classification, since until task 13 a failure is skipped and its offset committed with no result (trade-off "Failed classification"). `LAG` above 0 shows products still to classify, read or not.
5. Start the service again. The broker keeps the dead consumer in the group until its session times out (`session.timeout.ms`), so the new one may sit idle before it gets the partitions. Once it is done, `LAG` is 0 on every partition and every product of the sample is on `produtos-classificados` or `produtos-revisao` (see the README).

### Break it
Turn on the client's auto-commit and run the crash test, which throws an `Error` before publishing, as a fatal error such as `OutOfMemoryError` would:

```sh
./mvnw -Dtest=ClassificationListenerTest#productIsClassifiedAgainWhenTheServiceDiesBeforePublishing \
  -Dspring.kafka.consumer.enable-auto-commit=true test
```

It fails with `no record with key P009 in produtos-classificados`: the consumer that stopped committed its position on close, past the product it never published, so the product is lost. Without the flag the test passes.

### What it does not cover
A death between the send and the commit publishes the result twice and pays for the AI twice (trade-off "At-least-once").
