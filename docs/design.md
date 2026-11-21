# Design

How the classifier is built. This document grows with each task of the [roadmap](roadmap.md).

## Architecture

Hexagonal (ports and adapters) inside a single Maven module, under the base package `com.ebravo.taxclassifier`:

| Package | Responsibility | May depend on |
|---|---|---|
| `domain` | Products, tax classification and the rules around them | The JDK core only |
| `application` | Use cases that drive the domain through ports | `domain` and the JDK core only |
| `adapter` | Kafka, the official calculator over HTTP, the Claude API | `application`, `domain` |

Dependencies point inward. `ArchitectureTest` enforces the rules on the production code, and `ArchitectureRulesTest` proves each rule fires on test fixtures that break it.

## Technology

- Java 25, Spring Boot 4.1.
- Maven through the Maven Wrapper.
- Spring Kafka on Apache Kafka 4.2 (KRaft); Jackson 3 for JSON.
- JUnit, AssertJ, ArchUnit, JaCoCo, PIT (mutation testing), Testcontainers (a real Kafka broker in tests).

## Decisions

| # | Decision | Why |
|---|---|---|
| D1 | Single Maven module, layering enforced by ArchUnit whitelists | Keeps the build simple. A whitelist also keeps out libraries nobody thought to forbid: Spring, Kafka, HTTP clients, the Claude SDK. The JDK core is `java.lang`, `java.util`, `java.math` and `java.time`; I/O, networking and SQL belong to adapters. Patterns are anchored to the base package, so a library's own `domain` package does not pass as ours. |
| D2 | 100% line, branch and mutation coverage on `domain` and `application`, enforced in `verify` | The tax rules decide what reaches an accountant for review; every rule is exercised, and PIT checks the tests would notice it break. Adapters are tested only where they hold behavior of this service. |
| D3 | Dependencies pinned to releases published at least two weeks earlier | A fresh release has had no time to show its regressions. Spring Boot 4.1.2 was chosen over 4.2.0 and 4.1.3, both published on 2026-11-19. CI actions are pinned to exact releases, and the Maven Wrapper checks the SHA-256 of the Maven it downloads. |
| D4 | The official calculator runs as a separate process, pinned at tag `1.6.1` (commit `150cccb`, database `V0059`) and built on JDK 25 | Its code stays out of this repository, and a fixed release keeps the recorded responses valid. `1.6.1` was released on 2026-10-27 (commit of 2026-10-22); `1.7.2` came out on 2026-11-19, too recent under D3. The tag declares Java 21; on JDK 25 it builds with `-Dmaven.compiler.proc=full`, because JDK 23 stopped running annotation processors such as Lombok by default. One JDK serves both projects. |
| D5 | The AI chooses from the full official CBS/IBS list of CST and `cClassTrib` for the date; the calculator checks the link to the NCM | Release `1.6.1` lists `cClassTrib` by CST, date, NBS or actor, never by NCM (18 CSTs and 164 `cClassTrib` for CBS/IBS on 2027-03-10). The Imposto Seletivo lists are separate endpoints and stay out, as the scope calculates CBS and IBS only. An NCM can only be checked against one `cClassTrib` at a time, through `GET /dados-abertos/classificacoes-tributarias/ncm-aplicavel` or the `REG-011` error of the calculation. An unlinked choice comes back as `INCONSISTENCIA_ENTRADA` with `REG-011` and goes to review. |
| D6 | The domain reads the products CSV from lines already read; every data line becomes a product or a rejection with its line number and reason | The domain stays free of I/O (D1); the publish command of task 03b reads the file. The header is `code;description;ncm;price`, fields are split on `;` and kept as written, the code has no surrounding spaces (it is the product's identity, so it is rejected, never trimmed), the NCM has 8 digits and the price is a positive decimal with at most 2 places. A price is kept at scale 2 with `RoundingMode.UNNECESSARY`, so it is never rounded: a third decimal rejects the line. A missing or wrong header rejects line 1 and every data line, since the columns cannot be trusted. A repeated code is not an error: it is an update of the same product. |
| D7 | Products are published to `produtos` keyed by product code, as JSON with the price as a string, with `acks=all` and idempotence | The key sends every update of a product to the same partition, the only place Kafka keeps order, so a product's updates are classified in order (scenario 5). The value is `{"code","description","ncm","price"}` with the price as written at scale 2 (`"25.90"`): a JSON number would let a consumer read it as a double. No Jackson type header: the contract is the JSON alone. The topic has 3 partitions, room for the two consumer instances of the rebalance scenario, and one replica on the single local broker. `acks=all` and `enable.idempotence=true` are kafka-clients defaults, set explicitly so a conflicting setting makes the producer fail at start. The publisher sends every product before waiting for any; a failure part way leaves earlier products published, which is safe because publishing again is an update under the same key. |
| D8 | `java -jar target/tax-classifier.jar publish <file>` publishes a products CSV and answers with its exit code: 0 every line published, 1 the valid lines published and some rejected, 2 nothing guaranteed published | A script or CI job can tell a catalog with errors from a publish that did not happen. Each rejected line goes to stderr as `line N: <reason>`, a one-line summary to stdout, and a failure (missing file, a file that is not UTF-8, the broker unreachable, no file given) to stderr as one `publish failed: <reason>` line, with the root cause of a Kafka error. The file is read strictly as UTF-8 and a leading byte order mark, which Excel writes, is removed. Without the `publish` argument the application starts and returns without publishing; options may come before it. stdout holds the summary only: the banner is off and logs below WARN are dropped, the Kafka client's below ERROR and Spring Kafka's entirely, since the command reports a Kafka failure itself. The admin operation and `max.block.ms` wait 10 s each, so an unreachable broker is reported in about 30 s (34 s measured) instead of 106 s. |
