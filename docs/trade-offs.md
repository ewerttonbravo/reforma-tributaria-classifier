# Trade-offs

Compromises made consciously and why. This document grows with each task.

Each compromise has a kind: a **design choice** would stay the same with more time; a **temporary** one has a planned removal.

| Area | Kind | Compromise | Why | Way out |
|---|---|---|---|---|
| Module boundaries | Design choice | Layering enforced by tests, not by the compiler | A single module keeps the build simple | Split into Maven modules if the codebase grows |
| Build time | Design choice | PIT runs in every `verify`, locally and in CI | A mutation gate that runs only on demand stops being run | Incremental analysis (`withHistory`) if the build gets slow |
| Empty layers | Temporary | The ArchUnit rules allow an empty layer (`allowEmptyShould`) and PIT passes with no mutations (`failWhenNoMutations=false`) | `domain` and `application` have no classes yet; the rules are proven on fixtures meanwhile | Remove both with the first class of each layer (task 02 for `domain`) |
