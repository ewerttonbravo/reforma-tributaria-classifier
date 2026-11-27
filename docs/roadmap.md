# Roadmap

Approved by the maintainer on 2026-11-20; task 03 split into 03a and 03b on 2026-11-21 to stay under the size gate; task 06 split into 06a, 06b and 06c on 2026-11-23, for size and because the legal basis comes from the calculator, not the AI; task 07 split into 07a, 07b and 07c on 2026-11-24 to stay under the size gate. Task 08 split into 08a and 08b on 2026-11-25 to stay under the size gate, since the calculator requires nominal rates from 2027 on. Task 11 split into 11a and 11b on 2026-11-27 to stay under the size gate. Task 13 took over the malformed record on `produtos` on 2026-11-24, at the elucidation of task 07b. The unit of work is the task: one observable behavior, one small PR.

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
| 01 | Official calculator | The calculator runs on `:8080` at a pinned commit, with the steps in the README; real responses from the 3 endpoints saved as fixtures; settles whether the calculator lists `cClassTrib` per NCM (it does not, design D5) | 00; JDK 25 for the calculator |
| 02 | Read products CSV | Domain: CSV line → `Product` (code, description, NCM, `BigDecimal` price); an invalid line is reported with its line number, never dropped silently | 00 |
| 03a | Kafka product publisher | Use case publishes the valid products of the CSV through a port; the Kafka adapter sends them to `produtos` (3 partitions) with key = product code, a JSON value with the price as a string, `acks=all` and idempotence; Testcontainers proves the same key lands on the same partition, in order | 02 |
| 03b | Publish command | `compose.yaml` with Kafka in KRaft mode; 1 command reads the CSV file (UTF-8, BOM removed), publishes the valid lines, prints each rejected line with its number and reason to stderr and exits with 1 if any line was rejected; sample CSV | 03a |
| 04 | Official candidates | Port + HTTP adapter: given a date, returns the official CBS/IBS list of CST / `cClassTrib` (the calculator has no list per NCM, see design D5); contract test with the fixtures from 01 | 01 |
| 05 | Validate AI suggestion | Domain: a suggestion outside the list or below the confidence threshold → review with a reason; inside → accepted | 00 |
| 06a | Claude adapter | Port + `claude-haiku-5-5` adapter with structured JSON output: the answer becomes a `Suggestion` with its justification; contract via HTTP stub; manual checklist with the real key | 05 |
| 06b | AI failures | A refusal, a truncated answer (`max_tokens`), a malformed answer or an HTTP error is a failure, never a suggestion; the SDK does not retry (retry is task 12) | 06a |
| 06c | Official legal basis | Port + adapter: the official legal basis for a date and `cClassTrib`, from `GET /dados-abertos/fundamentacoes-legais`; contract with the fixtures from 01 | 01 |
| 07a | Classify a product | Use case: the official list of the day in São Paulo, the AI's suggestion, the check and the official legal basis give a classification (without legal basis when none is published) or a review with its reason; in-memory fakes | 04, 05, 06b, 06c |
| 07b | Classification consumer | Consumes `produtos` and publishes to `produtos-classificados` or `produtos-revisao`; confidence threshold from configuration; the publish command does not start the consumer (Testcontainers + fakes) | 03a, 07a |
| 07c | Official lists cached by date | The calculator adapters read the official list and the legal bases once per date, keeping only the latest date; a failure is never cached | 07a |
| 08a | Calculate CBS/IBS | Port + `POST /observabilidade/regime-geral` adapter: a product and its suggestion, at an instant sent at `-03:00` and in the municipality from configuration, give a `sealed` result per `estadoItem`, the amounts or the calculator's errors; contract with responses recorded from 1.6.1 | 01 |
| 08b | Nominal rates from 2027 | From 2027-01-01 the request carries the nominal CBS/IBS rates from configuration, which the calculator requires (`REG-037`), and the result is simulated; before that date none are sent, as the calculator forbids them | 08a |
| 09 | Route by estadoItem | Classified with CBS/IBS amounts for `CALCULADO` / `CALCULO_SIMULADO`; `INCONSISTENCIA_ENTRADA` and `REG-011` → review (scenario 4) | 07b, 08b |
| 10 | Manual offset commit | Offset committed only after publishing; a crash before publishing → reprocessed | 09 |
| 11a | Suggestion store | Port + PostgreSQL adapter keep the AI's suggestion per product code and a hash of its description and NCM, the price left out; `compose.yaml` runs PostgreSQL; the publish command runs without it (Testcontainers) | 10 |
| 11b | Idempotency | A duplicate message calls the AI only once (scenario 1): its saved suggestion is checked, calculated and published again; a crash after the AI answers does not pay for it twice | 11a |
| 12 | Retry with backoff | Calculator `FALHA_TECNICA` / timeout and AI 429 retried with backoff; calculator goes down and comes back → recovers (scenario 2) | 10 |
| 13 | Exhausted retry → review | A permanent failure goes to `produtos-revisao` with its cause (scenario 3); a malformed record on `produtos` goes there too instead of being skipped | 12 |
| 14 | Ordering per product | Two updates of the same product are processed in order (scenario 5) | 12 |
| 15 | Rebalance | 2 instances, one killed mid-processing: nothing lost, nothing duplicated (scenario 6) | 11b |
| 16 | End to end | `docker compose up` + calculator + 1 command process the sample CSV; README with diagram, tax-liability notice and the pinned calculator version; checks that the 5 required ADRs exist | 15, 03b |
| 17 | Release | Repository description and topics set; the repository is public since task 00 | 16; every failure scenario reproduced and broken by hand |

Tasks 10 to 15 each close with their section in `docs/failure-scenarios.md`.

## Open decisions (settled at the elucidation of the task shown)
- **12 × 14:** non-blocking retry (`@RetryableTopic`) moves the message to retry topics and breaks per-product ordering, which scenario 5 requires. Blocking retry (`DefaultErrorHandler` with backoff) keeps the order but stalls the partition.

The order follows the phases: base, AI classification, calculation, resilience, proof. Task 01 comes early because the official calculator is the largest external unknown.
