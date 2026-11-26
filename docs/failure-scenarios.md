# Failure scenarios

How to reproduce each failure scenario by hand, and how to break the protection that handles it. This document grows with tasks 10 to 15 of the [roadmap](roadmap.md).

Every section assumes Kafka running (`docker compose up -d`), the official calculator on `:8080` and the service built (`./mvnw -DskipTests package`), as in the [README](../README.md).

## Crash before publishing (task 10)

The service commits a product's offset only after its result is sent (D20). A product in flight when the service dies is classified again when it comes back.

### Reproduce
1. Start the service with a Claude API key: `java -jar target/tax-classifier.jar`.
2. In another terminal, publish the sample: `java -jar target/tax-classifier.jar publish samples/products.csv`.
3. A few seconds later, before the 11 products are done (each one waits on the AI), kill the service without a shutdown: `kill -9 $(pgrep -f 'tax-classifier.jar$')`.
4. See what the group committed:
   ```sh
   docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --describe --group tax-classifier
   ```
   At least one partition of `produtos` has `LAG` above 0: the products read but not published.
5. Start the service again and run the command of step 4 once it goes quiet: `LAG` is 0 on every partition, and every product of the sample is on `produtos-classificados` or `produtos-revisao` (see the README).

### Break it
Turn on the client's auto-commit and run the crash test:

```sh
./mvnw -Dtest=ClassificationListenerTest#productIsClassifiedAgainWhenTheServiceDiesBeforePublishing \
  -Dspring.kafka.consumer.enable-auto-commit=true test
```

It fails with `no record with key P009 in produtos-classificados`: the consumer that died committed its position on close, past the product it never published, so the product is lost. Without the flag the test passes.

### What it does not cover
A death between the send and the commit publishes the result twice and pays for the AI twice (trade-off "At-least-once").
