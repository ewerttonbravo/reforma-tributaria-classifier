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
- JUnit, AssertJ, ArchUnit, JaCoCo, PIT (mutation testing).

## Decisions

| # | Decision | Why |
|---|---|---|
| D1 | Single Maven module, layering enforced by ArchUnit whitelists | Keeps the build simple. A whitelist also keeps out libraries nobody thought to forbid: Spring, Kafka, HTTP clients, the Claude SDK. The JDK core is `java.lang`, `java.util`, `java.math` and `java.time`; I/O, networking and SQL belong to adapters. Patterns are anchored to the base package, so a library's own `domain` package does not pass as ours. |
| D2 | 100% line, branch and mutation coverage on `domain` and `application`, enforced in `verify` | The tax rules decide what reaches an accountant for review; every rule is exercised, and PIT checks the tests would notice it break. Adapters are tested only where they hold behavior of this service. |
| D3 | Dependencies pinned to releases published at least two weeks earlier | A fresh release has had no time to show its regressions. Spring Boot 4.1.2 was chosen over 4.2.0 and 4.1.3, both published on 2026-11-19. CI actions are pinned to exact releases, and the Maven Wrapper checks the SHA-256 of the Maven it downloads. |
| D4 | The official calculator runs as a separate process, pinned at tag `1.6.1` (commit `150cccb`, database `V0059`) and built on JDK 25 | Its code stays out of this repository, and a fixed release keeps the recorded responses valid. `1.6.1` was released on 2026-10-27 (commit of 2026-10-22); `1.7.2` came out on 2026-11-19, too recent under D3. The tag declares Java 21; on JDK 25 it builds with `-Dmaven.compiler.proc=full`, because JDK 23 stopped running annotation processors such as Lombok by default. One JDK serves both projects. |
| D5 | The AI chooses from the full official CBS/IBS list of CST and `cClassTrib` for the date; the calculator checks the link to the NCM | Release `1.6.1` lists `cClassTrib` by CST, date, NBS or actor, never by NCM (18 CSTs and 164 `cClassTrib` for CBS/IBS on 2027-03-10). The Imposto Seletivo lists are separate endpoints and stay out, as the scope calculates CBS and IBS only. An NCM can only be checked against one `cClassTrib` at a time, through `GET /dados-abertos/classificacoes-tributarias/ncm-aplicavel` or the `REG-011` error of the calculation. An unlinked choice comes back as `INCONSISTENCIA_ENTRADA` with `REG-011` and goes to review. |
