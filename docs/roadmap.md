# Roadmap

Approved by the maintainer on 2026-11-20. The unit of work is the task: one observable behavior, one small PR.

## Frame of every task
**Elucidate**, before any code:
1. List what binds the task: design decisions (`docs/design.md`), accepted trade-offs, external dependencies (official calculator, Claude API, Kafka) and open decisions.
2. Turn gaps into closed questions with named options.
3. Split into at most ~3 TDD cycles (`test:` → `feat:` → `refactor:`).
4. Size gate: 1 observable behavior and a diff of ≤ ~200 hand-written lines, tests included (the Maven Wrapper and fixtures copied from the official examples do not count). If it goes over, split the task before starting.

**Build:** branch `task/NN-name` from `main`, TDD with targeted runs (`./mvnw -Dtest=Class#method test`), full `./mvnw clean verify` only at milestones.

**Close:** `./mvnw clean verify` green (100% line and branch coverage and 100% PIT mutation score on `domain` and `application`) · contract tests for every adapter touched · code review · `docs/design.md` and `docs/trade-offs.md` updated · failure-scenario tasks add a "how to reproduce and break it by hand" section to `docs/failure-scenarios.md` · PR with evidence per acceptance criterion · merge commit only after approval.

## Test strategy
- **`domain` and `application` (pure Java):** CSV parsing, validation of the AI suggestion against the official list, routing by `estadoItem`, idempotency rules. Unit tests with in-memory fakes, 100% line and branch coverage and 100% PIT.
- **Adapters:** Kafka, the official calculator over HTTP, the Claude API. Tested only on behavior that is ours: consumption, retry, DLQ, idempotency, ordering and the contracts with the calculator and the AI. No tests that only cover framework glue.
- **Kafka:** integration tests against a real broker via Testcontainers.
- **No network in tests:** the Claude API is never called and the calculator never needs to be running. HTTP stubs replay real responses copied from the official examples.
- **Boundaries made explicit:** confidence threshold, empty candidate list, each `estadoItem`, `BigDecimal` scale and rounding, `-03:00` offset in `dhFatoGerador`.

## Tasks (1 PR each)
| # | Task | Observable outcome | Depends on |
|---|---|---|---|
| 00 | Skeleton | Spring Boot on Java 25 with the Maven Wrapper; ArchUnit enforces `domain` / `application` / `adapter`; the JaCoCo and PIT gates are proven to fail on untested code; CI on GitHub Actions | GitHub repository |
| 01 | Official calculator | The calculator runs on `:8080` at a pinned commit, with the steps in the README; real responses from the 3 endpoints saved as fixtures; confirms which endpoint returns `cClassTrib` per NCM | 00 |
| 02 | Read products CSV | Domain: CSV line → `Product` (code, description, NCM, `BigDecimal` price); an invalid line is reported with its line number, never dropped silently | 00 |
| 03 | Publish products | `compose.yaml` with Kafka in KRaft mode; 1 command publishes the CSV to `produtos` with key = product code, `acks=all` and idempotence; Testcontainers proves the same key lands on the same partition | 02 |
| 04 | Official candidates | Port + HTTP adapter: given a date, returns the official list of CST / `cClassTrib` (the calculator has no list per NCM, see design D5); contract test with the fixtures from 01 | 01 |
| 05 | Validate AI suggestion | Domain: a suggestion outside the list or below the confidence threshold → review with a reason; inside → accepted | 00 |
| 06 | Claude adapter | Port + `claude-haiku-5-5` adapter with structured JSON output; contract via HTTP stub; manual checklist with the real key | 05 |
| 07 | Classification consumer | Consumes `produtos` and publishes to `produtos-classificados` or `produtos-revisao` (Testcontainers + fakes) | 03, 04, 06 |
| 08 | Calculate CBS/IBS | Port + `POST /observabilidade/regime-geral` adapter; `estadoItem` → `sealed` result; `dhFatoGerador` at `-03:00`; contract with the official examples | 01 |
| 09 | Route by estadoItem | Classified with CBS/IBS amounts for `CALCULADO` / `CALCULO_SIMULADO`; `INCONSISTENCIA_ENTRADA` and `REG-011` → review (scenario 4) | 07, 08 |
| 10 | Manual offset commit | Offset committed only after publishing; a crash before publishing → reprocessed | 09 |
| 11 | Idempotency | A duplicate message calls the AI only once (scenario 1); PostgreSQL vs H2 decided at elucidation | 10 |
| 12 | Retry with backoff | Calculator `FALHA_TECNICA` / timeout and AI 429 retried with backoff; calculator goes down and comes back → recovers (scenario 2) | 10 |
| 13 | Exhausted retry → review | A permanent failure goes to `produtos-revisao` with its cause (scenario 3) | 12 |
| 14 | Ordering per product | Two updates of the same product are processed in order (scenario 5) | 12 |
| 15 | Rebalance | 2 instances, one killed mid-processing: nothing lost, nothing duplicated (scenario 6) | 11 |
| 16 | End to end | `docker compose up` + calculator + 1 command process the sample CSV; README with diagram, tax-liability notice and the pinned calculator version; checks that the 5 required ADRs exist | 15 |
| 17 | Release | Repository description and topics set; the repository is public since task 00 | 16; every failure scenario reproduced and broken by hand |

Tasks 10 to 15 each close with their section in `docs/failure-scenarios.md`.

## Open decisions (settled at the elucidation of the task shown)
- **12 × 14:** non-blocking retry (`@RetryableTopic`) moves the message to retry topics and breaks per-product ordering, which scenario 5 requires. Blocking retry (`DefaultErrorHandler` with backoff) keeps the order but stalls the partition.
- **09:** where `NAO_IMPLEMENTADO` is routed.

The order follows the phases: base, AI classification, calculation, resilience, proof. Task 01 comes early because the official calculator is the largest external unknown.
