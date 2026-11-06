# add_parameter on a record: a component

- **Item:** roadmap #129 (gorefactor's `add_field`, parity section 3). The user's choice
  (2026-11-06): `add_parameter` on a record's canonical constructor adds a component, over a new
  `add_field` tool; for a class, the composition stays add_declaration + add_parameter +
  insert_statements.
- **Date:** 2026-11-06
- **Branch:** add-parameter-record

## Plan

`add_parameter` with `name` a record (`Line`) whose canonical constructor is implicit or compact
adds a component (`record Line(String sku, int qty, int unitCents)`), at the end or before
`before`, and every `new Line(...)` and `this(...)` passes `value`. Refused: a record that writes
its canonical constructor out (a parameter there is not a component), a component of that name,
a record pattern of it (its arity changes).

Derivation: golden first; `Signatures.addComponent`, reached from `addParameter` when the name is
a record of the file.

## Notes

- read: `Signatures.addParameter`, `MethodFamily.of`'s refusal.
- The golden first gave `value=0` in `args.properties` (read as a number; the session notes say
  so): moved to `value.txt`.
- refusal: the batch, for `after` naming an overload by its label (`addParameter(SourceProject,
  Args)`), which the refusal itself listed; resent with `after: parsesAs`. Friction-log entry.

## Measured

Through jrefactor: one batch refused, then resent; the docs; by hand the golden (resources) and
this record.

## Assessment

Through the tools. The resend cost 120 lines for one argument (the batch is all or nothing).

## Follow-ups

- A field added to a class with every constructor filled, if a task asks (today the composition).
