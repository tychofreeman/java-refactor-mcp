---
order: 25
item: 32
status: Partly done
---

## Item

Split large classes when each is next changed: `Extract` (1,048 lines), `Signatures` (753), `Declarations` (597, 26 static methods), `Tools` (schemas and dispatch)

## Status

Partly done, cloud session

## Evidence

Code review 2026-11-03. No behavior change; no split without a change that needs it. `Signatures` is split (`MethodFamily`, `SignatureChange`; the other session, 2026-11-03). `Extraction` (the nested class of `Extract`, about 660 lines) is its own file (`docs/sessions/split-extract.md`); `Signatures`, `Declarations` and `Tools` are not split; `Signatures` (818 lines) split by `move_type` into `Signatures` (365, the entry points and shared helpers), `MethodFamily` (327) and `SignatureChange` (153), record in `docs/sessions/split-signatures.md`. Placement by kind left `Declarations` for `Placement` and `Place` (2026-11-08, `docs/sessions/split-placement.md`), by `move` and `convert_lambda`
