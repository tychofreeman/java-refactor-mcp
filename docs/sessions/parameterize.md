# Parameterize: extract_variable as=parameter over the method's parameters

- **Item:** roadmap #125 (gorefactor's `parameterize`, parity section 3). The user's choice
  (2026-11-06): widen `extract_variable` `as=parameter`, over a new tool.
- **Date:** 2026-11-06
- **Branch:** parameterize

## Plan

`as=parameter` takes an expression whose names are the enclosing method's parameters and
constants (it took literals and constants only): the method gets the parameter, the expression
is replaced by its name, and each call passes the expression with that call's arguments in
place of the parameters (`total(n + 1, 3)` passes `(n + 1) / 10` for `qty / 10`). Refused: a
parameter the body assigns (its value at the expression is not the argument), a local, an
instance field or a call in the expression, and a call whose argument for a substituted
parameter has effects (it would run twice).

Derivation: goldens first; `Signatures.addParameter` split into the tool and a core taking the
value per call (`replace_declaration`); `Introduce`'s check and `parameter` pass the function.

## Notes

- read: `Introduce` (the check and `parameter`), `Signatures.addParameter`, the `Call` record.
- One batch (the split, the check, the per-call value, the call); my golden named the wrong
  line, then a refusal fragment worded otherwise; both fixed in the golden.

## Measured

Through jrefactor: 2 edit batches and the docs; no refusals; by hand the goldens (resources) and
this record.

## Assessment

Through the tools; as planned. `add_parameter` itself still takes one value for every call.

## Follow-ups

- Instance fields in the expression (each call would pass `receiver.field`).
