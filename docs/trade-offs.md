# Trade-offs

Compromises made consciously and why. This document grows with each task.

Each compromise has a kind: a **design choice** would stay the same with more time; a **temporary** one has a planned removal.

| Area | Kind | Compromise | Why | Way out |
|---|---|---|---|---|
| Module boundaries | Design choice | Layering enforced by tests, not by the compiler | A single module keeps the build simple | Split into Maven modules if the codebase grows |
| Build time | Design choice | PIT runs in every `verify`, locally and in CI | A mutation gate that runs only on demand stops being run | Incremental analysis (`withHistory`) if the build gets slow |
| Empty layers | Temporary | The ArchUnit rules allow an empty layer (`allowEmptyShould`) and PIT passes with no mutations (`failWhenNoMutations=false`) | `domain` and `application` have no classes yet; the rules are proven on fixtures meanwhile | Remove both with the first class of each layer (task 02 for `domain`) |
| AI prompt size | Design choice | The AI receives all 164 `cClassTrib`, not only those linked to the product's NCM | The calculator offers no list by NCM (D5); checking each candidate would take 164 calls per product | Pre-filter with `ncm-aplicavel` or the calculator's annex tables if cost or accuracy demand it |
| Calculator build | Temporary | The calculator is built with `-Dmaven.compiler.proc=full`, a flag its README does not mention | Release `1.6.1` targets Java 21 and does not build on JDK 25 without it (D4) | Drop the flag when a pinned release builds on JDK 25 as published |
