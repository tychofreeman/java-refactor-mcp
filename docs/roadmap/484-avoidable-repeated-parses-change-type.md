---
order: 474
item: 484
status: Done
---

## Item

Avoidable repeated parses: `change_type` parsed the field's files twice, `extract_variable` `as=parameter` with a call parsed the calling file once per call (`ParameterValue.sameTargets`), `TypeParameters.ordinal` walked the whole file once per call, and `move` with private callees runs a whole `move` per callee (issue #529, code review)

## Status

Done 2026-11-14: one `Holding` in `ChangeType.run` for `checkUses` and `CallTargets`; `ParameterValue.probe` writes every call's argument into its file and parses it once, run by `PerCall.probe` from `Introduce`'s `more` before the per-call checks, each call's refusal thrown in its turn; `TypeParameters.ordinals`/`byOrdinal` index a file in one walk. `GateCostTest` prints binding parses (`Session.bindingParses`). Measured (2026-11-14, a generated project of 22 files with 1,000 calls, and Commons Lang): `extract_variable` `as=parameter` of `o.qty()`, 1,045 binding parses and 10.5 s before, 65 and 3.1 s after; `add_parameter` `type_parameter` on a method with 1,000 calls, 1.2 s to 0.9 s; `change_type` `NumericEntityEscaper.below` to `long`, 7 binding parses to 6. `withAlong` is left as it is (default): `move` with three private callees cost 4 gate counts and 37 binding parses, 1.6 s, on 22 files. No behavior change; golden `extract_variable_parameter_call_other_method` pins the "calls another method" refusal with two calls probed in one file (it passed before too)

## Evidence

Issue #529; `docs/sessions/fewer-parses.md`
