# Failure scenarios

How to reproduce each failure scenario by hand, and how to break the protection that handles it. This document grows with tasks 10 to 15 of the [roadmap](roadmap.md).

Every section assumes Kafka and PostgreSQL running (`docker compose up -d`), the official calculator on `:8080` and the service built (`./mvnw -DskipTests package`), as in the [README](../README.md).

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
5. Start the service again. The broker keeps the dead consumer in the group until its session times out (`session.timeout.ms`), so the new one may sit idle before it gets the partitions. Compare only once it is done or stopped: the commands of step 4 run seconds apart, so while the service consumes they read different moments. Once it is done, `LAG` is 0 on every partition and every product of the sample is on `produtos-classificados` or `produtos-revisao` (see the README).

### Break it
Turn on the client's auto-commit and run the crash test, which throws an `Error` before publishing, as a fatal error such as `OutOfMemoryError` would:

```sh
./mvnw -Dtest=ClassificationListenerTest#productIsClassifiedAgainWhenTheServiceDiesBeforePublishing \
  -Dspring.kafka.consumer.enable-auto-commit=true test
```

It fails with `no record with key P009 in produtos-classificados`: the consumer that stopped committed its position on close, past the product it never published, so the product is lost. Without the flag the test passes.

### What it does not cover
A death between the send and the commit publishes the result twice (trade-off "At-least-once"); the AI is still paid once, as the next section shows.

## Duplicate message (task 11b)

The service saves the AI's suggestion per product code, description and NCM as soon as the AI answers (D21, D22). A product read again is checked against the official list of the day, calculated on its own price and published again, without a new call to the AI.

### Reproduce
0. Start from an empty broker and database: `docker compose down && docker compose up -d` (neither has a volume).
1. Start the service with a Claude API key: `java -jar target/tax-classifier.jar`.
2. Publish the sample: `java -jar target/tax-classifier.jar publish samples/products.csv`. Its 11 lines hold 10 products: P001 comes twice, at two prices.
3. Once `LAG` is 0 (step 4 of the previous section), count the saved suggestions:
   ```sh
   docker compose exec postgres psql -U tax_classifier -c 'select count(*), max(created_at) from ai_suggestions'
   ```
   The count is 10: the second P001 reused the first one's suggestion, and both of its results carry that suggestion, at 25.90 and 26.50.
4. Publish the same file again and wait for `LAG` 0. The output topics hold 11 more results, and the query of step 3 shows the same count and the same `max(created_at)`: no product was sent to the AI again.

### Break it
Delete the saved suggestions and publish again:

```sh
docker compose exec postgres psql -U tax_classifier -c 'truncate ai_suggestions'
java -jar target/tax-classifier.jar publish samples/products.csv
```

Once `LAG` is 0, the query of step 3 shows 10 rows with a newer `max(created_at)`: every product was paid for again.

### What it does not cover
Two instances classifying the same product at once both pay for the AI, and both publish the first answer saved (D21, D22; task 15 runs two instances). A description saved once precomposed and once decomposed is two products to the store (D21).

## Calculator down and back (task 12b)

A calculator that does not answer, refuses the connection or answers 5xx fails as `CalculatorUnavailable` (D23), and the listener retries the product in place, with waits of 1, 2, 4, 8, 16 and 30 s (D24). The products behind it on the consumer's partitions wait too, so their order holds. A calculator back within that minute classifies the product, and the AI is not paid again, since its suggestion was saved before the calculation (D22).

### Reproduce
The calculator is stopped once the official lists are read, so the products reach the AI and fail at the calculation, after their suggestions are saved.

0. Start from an empty broker and database: `docker compose down && docker compose up -d`, with the calculator running.
1. Start the service with a Claude API key: `java -jar target/tax-classifier.jar`.
2. Publish one product and wait for it on `produtos-classificados`: the service has read and cached the day's official lists (D16).
   ```sh
   printf 'code;description;ncm;price\nP100;Arroz tipo 1 5kg;10063021;25.90\n' > /tmp/one.csv
   java -jar target/tax-classifier.jar publish /tmp/one.csv
   ```
3. Stop the calculator (Ctrl-C in its terminal) and publish the sample: `java -jar target/tax-classifier.jar publish samples/products.csv`. Each product is sent to the AI, its suggestion saved, and its calculation fails and is retried; the log shows the failed attempts.
4. Within 20 s, start the calculator again (see the README); it takes 19 to 23 s to start. Once it answers, the waiting products are classified.
5. Once `LAG` is 0 (step 4 of the first section), every product of the sample is on `produtos-classificados` or `produtos-revisao`, and the count of saved suggestions (step 3 of the previous section) is 11, P100 and the sample's 10: the retries did not pay for the AI again.

### Break it
Turn the retries off and repeat with the calculator stopped:

```sh
java -jar target/tax-classifier.jar --classification.retry.max-retries=0
```

Each product is tried once and skipped: none reaches an output topic, and `LAG` still drops to 0, since a skipped record's offset is committed. The same happens with the retries on when the calculator stays down for more than a minute: the product being retried is skipped, and the next one gets a fresh minute.

### What it does not cover
A skipped product reaches no output topic until task 13 sends it to review (trade-off "Failed classification"). A failure of PostgreSQL or of the send is not retried (trade-off "Database and send without retry").
