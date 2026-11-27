# reforma-tributaria-classifier

[![CI](https://github.com/ewerttonbravo/reforma-tributaria-classifier/actions/workflows/ci.yml/badge.svg)](https://github.com/ewerttonbravo/reforma-tributaria-classifier/actions/workflows/ci.yml)

Suggests the CST and `cClassTrib` of each product in a catalog for Brazil's consumption tax reform (CBS, IBS) and checks them against the official tax calculator of the Receita Federal. A suggestion is never a final classification: an accountant confirms it.

## Status

Skeleton, the official calculator and publishing a products CSV to Kafka: the build, the layer rules, the quality gates, the pinned calculator with its recorded responses, the CSV rules, the `produtos` topic, the publish command, and the classification service: the AI chooses from the official CST and `cClassTrib` list of the calculator, the calculator calculates CBS and IBS under that choice, and each product goes to `produtos-classificados` or to `produtos-revisao` by the calculator's answer. See the [roadmap](docs/roadmap.md).

## Build and test

Requires JDK 25 and a running Docker engine: the Kafka and PostgreSQL tests start a real broker and database with Testcontainers. The Maven Wrapper downloads Maven on first use.

With Colima instead of Docker Desktop, point Testcontainers at its socket first:

```sh
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

```sh
./mvnw verify                             # build, tests, coverage and mutation gates
./mvnw test                               # tests only
./mvnw -Dtest=ArchitectureTest test       # a single test class
```

`verify` fails below 100% line, branch and mutation coverage in `domain` and `application` ([design](docs/design.md#decisions)).

## Publish products

Start Kafka and PostgreSQL, build the jar once and publish the sample catalog of fictitious products:

```sh
docker compose up -d                                       # Kafka 4.2.2 (KRaft) on :9092, PostgreSQL 18.7 on :5432
./mvnw -DskipTests package
java -jar target/tax-classifier.jar publish samples/products.csv
```

Publishing does not use the database. The file is a UTF-8 CSV with the header `code;description;ncm;price` (a byte order mark is accepted). Each product goes to the `produtos` topic keyed by its code. The command prints one summary line and exits with:

| Code | Meaning |
|---|---|
| 0 | Every line was published |
| 1 | The valid lines were published, possibly none (a wrong header rejects every line); each rejected line is on stderr as `line N: <reason>` |
| 2 | Nothing is guaranteed published: `publish failed: <reason>` on stderr (missing file, not UTF-8, Kafka unreachable, which takes about 30 s to report) |

See what reached the topic, with the partition of each key:

```sh
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic produtos --from-beginning --formatter-property print.key=true --formatter-property print.partition=true
```

## Run the official calculator

The service calls the [official tax calculator](https://github.com/ReceitaFederal/calculadora-rtc-regime-regular) of the Receita Federal, pinned at tag `1.6.1` (commit `150cccb`). It runs as a separate process on port 8080; the service finds it at the `calculator.base-url` property, `http://localhost:8080/api/calculadora` by default. Calculations take place in the municipality of `calculator.municipality` (IBGE code) and `calculator.uf`, São Paulo (`3550308`, `SP`) by default. From 2027-01-01 the calculator requires nominal rates, in percent, which come from `calculator.nominal-rates.cbs`, `.ibs-state` and `.ibs-municipal` (8.8, 0.05 and 0.05 by default), and the calculation is simulated. Tests never need it: the contract tests of tasks 04, 06c, 08a and 08b replay its responses recorded in `src/test/resources/calculator/1.6.1/`.

Clone it outside this repository and build it with JDK 25:

```sh
git clone https://github.com/ReceitaFederal/calculadora-rtc-regime-regular.git
cd calculadora-rtc-regime-regular
git checkout 1.6.1
git rev-parse HEAD                          # 150cccbe414d4a5edea703e4f7a424af5aa222c7
mkdir -p ./calculadora/db
./mvnw flyway:migrate -Dflyway.configFiles=./flyway/flyway-pro.conf --batch-mode --errors
./mvnw clean package -DskipTests -Dmaven.compiler.proc=full
java -Dspring.profiles.active=offline -jar target/api-regime-geral.jar
```

Two flags differ from the official README. `-Dmaven.compiler.proc=full`: JDK 23 and later no longer run Lombok without it, and the build fails with `cannot find symbol`. `-DskipTests`: the calculator's own test suite is its maintainers' concern, not a prerequisite here. If `git rev-parse` prints another commit, the tag has moved and the fixtures may no longer match. Run the jar from the calculator's root, where the database lives.

Check the version and replay the calls behind the fixtures:

```sh
B=http://localhost:8080/api/calculadora
F=src/test/resources/calculator/1.6.1        # run from this repository's root
curl -s "$B/dados-abertos/versao"               # "versaoApp":"1.6.1"
curl -s "$B/dados-abertos/situacoes-tributarias/cbs-ibs?data=2027-03-10" | cmp - $F/situacoes-tributarias-cbs-ibs-2027-03-10.json
curl -s "$B/dados-abertos/situacoes-tributarias/cbs-ibs?data=2020-01-01" | cmp - $F/situacoes-tributarias-cbs-ibs-2020-01-01.json
curl -s "$B/dados-abertos/fundamentacoes-legais?data=2027-03-10" | cmp - $F/fundamentacoes-legais-2027-03-10.json
curl -s -X POST "$B/observabilidade/regime-geral" -H 'Content-Type: application/json' \
  -d @$F/observabilidade-regime-geral-request.json | cmp - $F/observabilidade-regime-geral-response.json
curl -s -D - -o /dev/null -X POST "$B/observabilidade/regime-geral" -H 'Content-Type: application/json' \
  -d @$F/observabilidade-regime-geral-request.json | tr -d '\r' \
  | grep -iE '^HTTP/|^X-CALC-|^x-warning-dados-simulados' | cmp - $F/observabilidade-regime-geral-response.headers
R=$F/regime-geral-p001-2026-request.json         # what the adapter sends for P001
post() { curl -s -X POST "$B/observabilidade/regime-geral" -H 'Content-Type: application/json' -d @-; }
post < $R | cmp - $F/regime-geral-p001-2026-calculado.json
sed 's/"cst":"000","cClassTrib":"000001"/"cst":"410","cClassTrib":"410001"/' $R | post | cmp - $F/regime-geral-p001-2026-imune.json
sed 's/"cst":"000","cClassTrib":"000001"/"cst":"200","cClassTrib":"200035"/' $R | post | cmp - $F/regime-geral-p001-2026-reg011.json
sed 's/"cst":"000","cClassTrib":"000001"/"cst":"620","cClassTrib":"620001"/' $R | post | cmp - $F/regime-geral-p001-2026-nao-implementado.json
post < $F/regime-geral-p001-2027-request.json | cmp - $F/regime-geral-p001-2027-simulado.json
```

`cmp` prints nothing when the response matches the fixture. The 400 fixture, `situacoes-tributarias-cbs-ibs-invalid-date-400.json` (from `?data=xx`), is left out: it carries a timestamp. The headers fixture keeps only the status line (verbatim, trailing space included), the version headers and `x-warning-dados-simulados`.

## Classify products

With Kafka, PostgreSQL and the calculator running and products published, start the classification service with a Claude API key:

```sh
export ANTHROPIC_API_KEY=...                # every product is one paid call to claude-haiku-5-5
java -jar target/tax-classifier.jar         # Ctrl-C to stop
```

The service consumes `produtos` as the consumer group `tax-classifier`, from the earliest offset on its first run. An accepted suggestion is calculated by the official calculator: when it calculates, the product goes to `produtos-classificados` with the official legal basis (`null` when none is published) and the CBS and IBS amounts, flagged `simulated` from 2027; when it rejects the item (`INCONSISTENCIA_ENTRADA`, such as `REG-011` for a `cClassTrib` not linked to the NCM, or `NAO_IMPLEMENTADO`), the product goes to `produtos-revisao` with the calculator's errors. A suggestion outside the official list or below the confidence threshold goes to `produtos-revisao` with its reason, without being calculated. The threshold is 0.80; change it with `--classification.confidence-threshold=0.90`. On start the service creates the table `ai_suggestions` in the PostgreSQL of `spring.datasource.url` (`jdbc:postgresql://localhost:5432/tax_classifier`), with the local credentials of `compose.yaml`; `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` override them. The database has no volume, so `docker compose down` deletes it with the topics. Until retries exist (task 12), a product whose classification fails, a calculator `FALHA_TECNICA` included, is logged and skipped, and so is a record on `produtos` that is not a product's JSON.

See the results:

```sh
for topic in produtos-classificados produtos-revisao; do
  docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
    --topic $topic --from-beginning --timeout-ms 5000 --formatter-property print.key=true
done
```
