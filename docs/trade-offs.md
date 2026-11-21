# Trade-offs

Compromises made consciously and why. This document grows with each task.

Each compromise has a kind: a **design choice** would stay the same with more time; a **temporary** one has a planned removal.

| Area | Kind | Compromise | Why | Way out |
|---|---|---|---|---|
| Module boundaries | Design choice | Layering enforced by tests, not by the compiler | A single module keeps the build simple | Split into Maven modules if the codebase grows |
| Build time | Design choice | PIT runs in every `verify`, locally and in CI | A mutation gate that runs only on demand stops being run | Incremental analysis (`withHistory`) if the build gets slow |
| AI prompt size | Design choice | The AI receives all 164 `cClassTrib`, not only those linked to the product's NCM | The calculator offers no list by NCM (D5); checking each candidate would take 164 calls per product | Pre-filter with `ncm-aplicavel` or the calculator's annex tables if cost or accuracy demand it |
| Calculator build | Temporary | The calculator is built with `-Dmaven.compiler.proc=full`, a flag its README does not mention | Release `1.6.1` targets Java 21 and does not build on JDK 25 without it (D4) | Drop the flag when a pinned release builds on JDK 25 as published |
| CSV format | Design choice | Fields are split on `;` with no quoting, so a description cannot contain `;` | The catalog is fictitious and one split keeps the parser small; a line with an extra `;` is rejected, never misread | An RFC 4180 parser in the adapter if real catalogs need it |
| Durability | Design choice | The local broker and the test broker keep one replica, so `acks=all` waits for the leader only | One machine runs the demo; three brokers would add nothing the tests can observe | Three brokers, replication factor 3 and `min.insync.replicas=2` in production |
