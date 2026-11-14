---
order: 167
item: 174
status: Done
---

## Item

The gate errored, not refused, when the JDT compiler (ECJ) itself threw while type-checking changed code (issue #405: a method reference against `Task<R, E>` once `E`'s bound became `Object & Comparable<E>`)

## Status

Done 2026-11-13 (`Jdt.compilerFailed`: `Jdt.parse` and `parseAll` turn an exception ECJ throws into a refusal naming it and where in ECJ, an exception of a unit's callback passing through; `Gate.checkedAtEnd` puts back the stubs it wrote, as `Gate.apply` did. The cause is ECJ's: javac reports three errors for that code, ECJ 3.47.0 throws on the reference's missing descriptor)

## Evidence

The Commons Lang stress run, `docs/sessions/stress-new-options.md`; `docs/sessions/fix-gate-jdt-errors.md`
