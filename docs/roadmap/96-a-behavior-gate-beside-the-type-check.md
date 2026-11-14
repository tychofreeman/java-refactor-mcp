---
order: 89
item: 96
status: Done
---

## Item

A behavior gate beside the type-check gate: a tool declares what its edit does to code (moved from here to there, duplicated, dropped), and one checker derives the effect-order (`Effects`), same-meaning (`NameCheck`) and exceptional-behavior (`Exceptional`) checks from that, for every refactoring

## Status

Done (2026-11-05), local session: the user chose declared moves (Decisions); `Behavior` (a `Move`: code, root, landings) derives the effect-order, place, copy, exceptional and same-meaning checks; `inline_variable` is its first user, which fixed a field read moved past a call that changes it (`docs/sessions/behavior-gate.md`); `extract_variable` second, which did #99 and found two gaps in the first version (a read crossed by a moved effect; a write later in a loop) and applies the policy's guard and try rules as refusals (`docs/sessions/gate-extract-variable.md`); `move_statement` third, which refuses a call moved past a read it could change and a statement that may throw lifted above a guard (`docs/sessions/gate-move-statement.md`); `inline_function` fourth, each argument a move into the body (`docs/sessions/gate-inline-function.md`). Done: the other tools in the evidence move whole statements, members or bodies to where they run at the same point in the flow, so what they need is `NameCheck`, which they run, and their one-off rules (`move_member`'s null receiver, `convert_lambda`'s receiver timing)

## Evidence

Review of the abstractions, 2026-11-04 (the user agreed): the three checks exist but each tool opts in, and most do not. `Effects` is used by 6 tools, `NameCheck` by 3, `Exceptional` by 5; `extract_method`, `move_member`, `pull_up`, `push_down`, `function_to_method` and `convert_lambda` move code without the effect check, and `preserve_exceptions` is accepted but does nothing in about 30 refactorings. Subsumes the per-tool parts of #75-#77. `InlineFunction` (1,038 lines, 20 refusals) should shrink as its rules move into the gate
