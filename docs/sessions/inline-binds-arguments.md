# inline_function binds arguments to locals (roadmap #102)

- **Item:** roadmap #102, from the A/B rerun (`docs/sessions/ab-warehouse-2.md`):
  `warehouse-inline` cost the mcp arm 17.8 turns against 10.5, with 20 refusals in
  4 runs, each correct but leaving the order of the fix to the model. The user's
  "yes" of 2026-11-05.
- **Date:** 2026-11-05
- **Branch:** inline-binds-arguments

## Decisions (this session)

- **Bind where in place would change behavior, as a standard inline does.** Each
  argument is tried in place through the gate; one it refuses (dropped, used twice,
  crossing an effect, in a branch, loop or lambda), or arguments `checkOrder` finds
  used out of order, are declared as locals before the call's statement. Every
  earlier argument that is not `fixed` is bound too, so all run once, in order.
  The reports a refused in-place try made are taken back.
- **The binding is itself checked:** declaring the first bound argument before
  the statement is an extract, so the gate checks it as one; refused, the call is
  refused with the in-place reason (`inline_function_refused_bind_crosses`:
  `return next() + twice(next());`).
- **The local** has the parameter's name and declared type (so the call's
  conversion is kept), numbered when the enclosing method or the body already uses
  the identifier.
- **The two-effects refusal is gone:** arguments with effects used in the order
  passed are fine in place; the rest are bound.
- **The statement only needs to be in a block** (`blockStatement`) for locals;
  a body with statements before its value still needs the call to be all of its
  statement (`wholeStatement`).

## Notes

- `read:` `replacementFor`, `substituted`, `wholeStatement`, to place the
  declarations.
- Nine refused goldens flipped to applied; each output was read before it was
  pinned (`inline_function_binds_*`): every argument still runs once, in order,
  at the call.
- `in_if` and `in_loop` first stayed refused: binding used `wholeStatement`, which
  takes only a declaration, a return or an assignment; `blockStatement` fixed it.
- A stub `inScope` came from a muddled name loop; rewritten, the stub deleted.
- The warehouse Yard inlines `both`, `either`, `within` in one call each, and the
  A/B's hidden acceptance test passes on the result.
- `hand:` none; the nine goldens' outputs were written by a script through a
  `bin/jrefactor` batch.

## Measured

All Java through jrefactor (`replace_text`, `add_declaration`,
`replace_declaration`, safe delete); goldens by `move_file` and `create_file`.

## Follow-ups

- An unused argument with an effect is bound to an unused local (`int x =
  next();`); an expression statement (`next();`) would read better.
- Rerun the A/B inline task to measure.
