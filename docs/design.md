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
