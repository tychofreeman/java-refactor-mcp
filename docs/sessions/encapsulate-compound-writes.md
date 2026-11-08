# encapsulate_field rewrites compound writes and ++

- **Item:** roadmap #130, its first part (gorefactor's `docs/parity.md` section 4: Java behind Go
  and TS on `encapsulate_field`'s compound writes and `++`). The user chose all four offered items
  (2026-11-07); this is the third.
- **Date:** 2026-11-07
- **Branch:** encapsulate-compound-writes

## Plan

The change: a write `encapsulate_field` refused because it was not a plain assignment becomes a
setter call when it is a statement of its own: `x op= v;` is `setX(getX() op (v));`, and `x++;`,
`++x;`, `x--;`, `--x;` are `setX(getX() + 1);` or `- 1`. Kept refused: a write whose value is
used (inside an expression), and a qualified write whose receiver is not a name or `this`, which
would then run twice.

Derivation:
1. Red: goldens `encapsulate_field_compound` (`+=` with a sum on the right, `*=`), 
   `encapsulate_field_increment` (`x++;`, `--p.x;`), `encapsulate_field_refused_increment_in_expression`
   (`int was = rate++;`), `encapsulate_field_refused_receiver_twice` (`next().rate += 1;`); the old
   `encapsulate_field_refused_compound` goes, as its case now passes.
2. Green: in `EncapsulateField.useEdit`, the compound branch and the `isIncDec` branch write the
   setter call through one helper, `setterOf(prefix, op, operand)`, when the write is an
   `ExpressionStatement` and the receiver is safe to repeat.
3. Docs: the description, the contract, parity in gorefactor (its own PR).

## Notes

- read: `EncapsulateField.useEdit` and its helpers (198-303), the code being changed.
- bulk: goldens and a probe (127 lines, resources, by shell), new tests.
- bulk: the two branches of `useEdit` by `replace_statements` (25 lines): mostly the existing
  "inside an expression" refusal moved ahead of the compound check, typed again in the new order
  with the compound rewrite after it. `move_statement` of the second `if` above the first would
  have moved it, then a smaller `replace_statements` of the compound refusal; one replacement was
  simpler to read back.
- bulk: `combined` and `operand` (55 lines), new behavior, through `add_declaration`.
- plan: the helper is `combined`, not `setterOf`, and also takes the refusal's context.
- A probe, `encapsulate_field_compound_order`, checks that a right-hand side that changes the field
  gives the same result: Java saves the field's value before the right-hand side, and the getter
  runs first too.

## Measured

From `bin/session-report`: one batch of three steps for the Java (two `replace_statements`, one
`add_declaration`), one of four for the descriptions and the contract, no refusals; by hand only
the record and test resources; reads: 2 `inspect`.

## Assessment

- **Through the tools or around them:** through them; the code passed every test on the first run.
- **Large writes:** the first `replace_statements` typed an existing refusal again to change the
  order of two `if`s (`move_statement` would have done that part).
- **Did the plan hold?** Yes.
- **One change that would have helped:** none needed.

## Follow-ups

- #130's other parts: `fold` of instance methods, line/column addressing on the signature tools.
- gorefactor's `docs/parity.md`: Java no longer behind on `encapsulate_field`'s compound writes
  and `++` (its own PR).
