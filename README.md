# reforma-tributaria-classifier

[![CI](https://github.com/ewerttonbravo/reforma-tributaria-classifier/actions/workflows/ci.yml/badge.svg)](https://github.com/ewerttonbravo/reforma-tributaria-classifier/actions/workflows/ci.yml)

Suggests the CST and `cClassTrib` of each product in a catalog for Brazil's consumption tax reform (CBS, IBS) and checks them against the official tax calculator of the Receita Federal. A suggestion is never a final classification: an accountant confirms it.

## Status

Skeleton and the official calculator: the build, the layer rules, the quality gates and the pinned calculator with its recorded responses. See the [roadmap](docs/roadmap.md).

## Build and test

Requires JDK 25. The Maven Wrapper downloads Maven on first use.

```sh
./mvnw verify                             # build, tests, coverage and mutation gates
./mvnw test                               # tests only
./mvnw -Dtest=ArchitectureTest test       # a single test class
```

`verify` fails below 100% line, branch and mutation coverage in `domain` and `application` ([design](docs/design.md#decisions)).

## Run the official calculator

The service calls the [official tax calculator](https://github.com/ReceitaFederal/calculadora-rtc-regime-regular) of the Receita Federal, pinned at tag `1.6.1`. It runs as a separate process on port 8080. Tests never need it: they replay its responses from `src/test/resources/calculator/1.6.1/`.

Clone it outside this repository and build it with JDK 25:

```sh
git clone https://github.com/ReceitaFederal/calculadora-rtc-regime-regular.git
cd calculadora-rtc-regime-regular
git checkout 1.6.1
mkdir -p ./calculadora/db
./mvnw flyway:migrate -Dflyway.configFiles=./flyway/flyway-pro.conf --batch-mode --errors
./mvnw clean package -DskipTests -Dmaven.compiler.proc=full
java -Dspring.profiles.active=offline -jar target/api-regime-geral.jar
```

`-Dmaven.compiler.proc=full` is not in the official README: JDK 23 and later no longer run Lombok without it, and the build fails with `cannot find symbol`. Run the jar from the calculator's root, where the database lives.

Check the version and replay the calls behind the fixtures:

```sh
B=http://localhost:8080/api/calculadora
F=src/test/resources/calculator/1.6.1        # run from this repository's root
curl -s "$B/dados-abertos/versao"               # "versaoApp":"1.6.1"
curl -s "$B/dados-abertos/situacoes-tributarias/cbs-ibs?data=2027-03-10" | cmp - $F/situacoes-tributarias-cbs-ibs-2027-03-10.json
curl -s "$B/dados-abertos/fundamentacoes-legais?data=2027-03-10" | cmp - $F/fundamentacoes-legais-2027-03-10.json
curl -s -X POST "$B/observabilidade/regime-geral" -H 'Content-Type: application/json' \
  -d @$F/observabilidade-regime-geral-request.json | cmp - $F/observabilidade-regime-geral-response.json
```

`cmp` prints nothing when the response matches the fixture.
