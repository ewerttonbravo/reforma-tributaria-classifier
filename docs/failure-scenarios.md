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
   The sum of `CURRENT-OFFSET` over the partitions of `produtos` is the number of products committed; the offsets of the two output topics add up to the results published. Committed is never above published: no product was committed without its result. A product whose classification failed counts as published too, since it goes to review before its offset is committed (D25); the only exception is a product logged as `Gave up sending to review`, committed with no result (trade-off "Review send failure"). `LAG` above 0 shows products still to classify, read or not.
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
3. Stop the calculator (Ctrl-C in its terminal) and publish the sample: `java -jar target/tax-classifier.jar publish samples/products.csv`. Each product is sent to the AI, its suggestion saved, and its calculation fails and is retried; the log shows `Record in retry and not yet recovered` at each attempt.
4. Within 20 s, start the calculator again (see the README); it took 19 to 26 s to start. Once it answers, the waiting products are classified.
5. Once `LAG` is 0 (step 4 of the first section), every product of the sample is on `produtos-classificados` or `produtos-revisao`, and the count of saved suggestions (step 3 of the previous section) is 11, P100 and the sample's 10: the retries did not pay for the AI again. Run by hand: the calculator answered 26 s after it was started, and the 12 results, 9 classified and 3 for review, were all out 56 s after that, the remaining products still waiting on the AI.

### Break it
Turn the retries off and repeat with the calculator stopped:

```sh
java -jar target/tax-classifier.jar --classification.retry.max-retries=0
```

Each product is tried once and sent to `produtos-revisao` as `FAILED`, with the `CalculatorUnavailable` that caused it (next section), instead of waiting for the calculator: run by hand, it reached review 0.7 s after it was published. The same happens with the retries on when the calculator stays down for more than a minute: run by hand, a product was tried 7 times, 1, 2, 4, 8, 16 and 30 s apart, and reached review 62 s after it was published; the next one gets a fresh minute.

### What it does not cover
A product whose retries ran out goes to review, not back to the calculator once it returns: someone has to publish it again. A failure of PostgreSQL or of the send is not retried (trade-off "Database and send without retry").

## Permanent failure (task 13)

A product whose classification fails for good, such as a refusal of the AI or a 4xx from the calculator, or whose retries ran out, goes to `produtos-revisao` with `reason` `FAILED` and the failure's message as `cause`, and only then is its offset committed (D25). A record on `produtos` that is not a product's JSON goes there too, with `product: null`, and a product missing a field goes with the field named. No product is left without a result.

### Reproduce
The calculator stays stopped from the start, so the product fails at the official list, before the AI is asked.

0. Start from an empty broker and database, `docker compose down && docker compose up -d`, with the calculator stopped.
1. Start the service with a Claude API key: `java -jar target/tax-classifier.jar`.
2. Publish one product and read the review topic:
   ```sh
   printf 'code;description;ncm;price\nP200;Arroz tipo 1 5kg;10063021;25.90\n' > /tmp/one.csv
   java -jar target/tax-classifier.jar publish /tmp/one.csv
   docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic produtos-revisao --from-beginning --property print.key=true
   ```
   Run by hand, P200 reached review 62 s after it was published, once its retries ran out (previous section):
   ```
   P200	{"product":{"code":"P200","description":"Arroz tipo 1 5kg","ncm":"10063021","price":"25.90"},"suggestion":null,"reason":"FAILED","errors":[],"cause":"the calculator is unavailable: I/O error on GET request for \"http://localhost:8080/api/calculadora/dados-abertos/situacoes-tributarias/cbs-ibs\": null"}
   ```
3. Send a record that is not JSON and a product without its NCM straight to `produtos`:
   ```sh
   printf 'P201:oops\nP202:{"code":"P202","description":"Feijão carioca 1kg","price":"8.49"}\n' |
     docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
       --topic produtos --property parse.key=true --property key.separator=:
   ```
   Both reach review at once, with no retry:
   ```
   P201	{"product":null,"suggestion":null,"reason":"FAILED","errors":[],"cause":"failed to deserialize"}
   P202	{"product":{"code":"P202","description":"Feijão carioca 1kg","ncm":null,"price":"8.49"},"suggestion":null,"reason":"FAILED","errors":[],"cause":"the product has no ncm"}
   ```
4. `LAG` is 0 on every partition (step 4 of the first section): the three records were committed after their reviews were sent.

### Break it
Put back the error handler's default recoverer, which logs a failed record and skips it, in `ClassificationConfiguration`:

```java
var handler = new DefaultErrorHandler(backOff);
```

and run `./mvnw -Dtest=ClassificationListenerTest#permanentFailureGoesToReviewWithItsCause test`. It fails with `no record with key P003 in produtos-revisao`: the refused product was committed with no result.

### What it does not cover
A failed send to `produtos-revisao` classifies the record again, up to 3 times, then logs it at ERROR and skips it (trade-off "Review send failure"); with one broker, this cannot be caused by hand without stopping the consumer too, so `ReviewRecovererTest` covers it. A malformed record's review carries neither its value nor its offset (trade-off "Malformed record in review"). A result whose own send failed reaches review as `FAILED`, without its suggestion (D25).

## Updates of the same product (task 14)

Every update of a product is published under its code, so it lands on the same partition of `produtos` (D7). The listener reads a partition in order and retries a failed record in place, so a later update waits behind one that is retried instead of overtaking it (D24). Its results go out under the same key, so they keep that order on the output topic (D15).

### Reproduce
The first update finds the calculator stopped; the second is published once the calculator is back, while the first still waits for its next attempt. The waits are raised to 90 s, past the 15 to 26 s the calculator takes to start, so there is time to publish the second while the first waits.

0. Start from an empty broker and database: `docker compose down && docker compose up -d`, with the calculator running.
1. Start the service with a Claude API key and waits of 90 s:
   ```sh
   java -jar target/tax-classifier.jar --classification.retry.initial-interval=90s \
     --classification.retry.max-interval=90s --classification.retry.max-retries=3
   ```
2. Publish one product and wait for it on `produtos-classificados`, so the day's official lists are cached (D16), as in step 2 of "Calculator down and back".
3. Stop the calculator and publish the first update:
   ```sh
   printf 'code;description;ncm;price\nP300;Arroz tipo 1 5kg;10063021;25.90\n' > /tmp/first.csv
   java -jar target/tax-classifier.jar publish /tmp/first.csv
   ```
   Once its suggestion is saved its calculation fails, and the log shows `Record in retry and not yet recovered`:
   ```sh
   docker compose exec postgres psql -U tax_classifier -c "select count(*) from ai_suggestions where product_code = 'P300'"
   ```
4. Start the calculator again (see the README). Once it answers, check that P300 has no result yet on `produtos-classificados` (step 5) and publish the second update:
   ```sh
   printf 'code;description;ncm;price\nP300;Arroz tipo 1 5kg;10063021;26.50\n' > /tmp/second.csv
   java -jar target/tax-classifier.jar publish /tmp/second.csv
   ```
5. Read the product's results:
   ```sh
   docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic produtos-classificados --from-beginning --formatter-property print.key=true \
     --formatter-property print.partition=true --formatter-property print.timestamp=true
   ```
   Both P300 results are on the same partition, 25.90 before 26.50. Run by hand: the second update was published 34 s after the calculator answered and waited 37 s behind the first; both came out at the first's next attempt, 52 ms apart, in order.

### Break it
Turn the retry non-blocking with Spring Boot's switch to retry topics, and run the ordering test:

```sh
./mvnw -Dtest=ClassificationListenerTest#updatesOfTheSameProductAreClassifiedInOrder \
  -Dspring.kafka.retry.topic.enabled=true test
```

It fails with the results as `["2.00", "1.00"]`: the first update was moved to `produtos-retry` to wait, and the second, which found the calculator up, was classified before it. Without the flag the test passes.

By hand, repeat the steps above with a new product code and the service started with retry topics that wait 90 s:

```sh
java -jar target/tax-classifier.jar --spring.kafka.retry.topic.enabled=true \
  --spring.kafka.retry.topic.attempts=3 --spring.kafka.retry.topic.backoff.delay=90s \
  --spring.kafka.retry.topic.backoff.max-delay=90s
```

The topics `produtos-retry` and `produtos-dlt` appear, and the first update waits on `produtos-retry`. Run by hand: the second update, published 34 s after the calculator answered, came out 6.6 s later, and the first came out 30 s after it, 90 s after it entered `produtos-retry`: 26.50 before 25.90. Without `max-delay` the wait stays at its default ceiling of 30 s whatever the `delay`: run by hand, the first update came back after 31 s, before the second could be published, and the order held by chance.

### What it does not cover
A product's results keep their order within each output topic, but not across them: an update sent to `produtos-revisao` and the next one classified on `produtos-classificados` carry nothing that orders them (trade-off "Order across output topics"). A death between a result's send and its commit publishes that result again before the next update's (trade-off "At-least-once"). Two instances of the service are task 15.
