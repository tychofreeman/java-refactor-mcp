# replace_statements with surround

- **Item:** roadmap #123, part 3 of 3 (surround with).
- **Date:** 2026-11-06
- **Branch:** replace-statements-surround

## Plan

`replace_statements` with `surround` in place of `content`: the statements from `start_line` to
`end_line` go inside a block statement with that head, one indentation step in: `if (ready)`,
`for (Item i : items)`, `while (more)`, `synchronized (lock)`, or `try`. A `try` catches the
checked exceptions the statements throw and rethrows them (`catch (IOException e) { throw e; }`,
a multi-catch for several, the name free in the method), so behavior is unchanged until the
model edits the handler; with none, an empty `finally`. Any other head is refused, naming the
forms. A local the statements declare and later code uses no longer compiles: the gate refuses.

Derivation: goldens first; `Surround.wrap` (from `declaration`, members by `add_declaration`),
the branch in `Primitives.replaceStatements`, the `Def` argument.

## Notes

- read: `Primitives.replaceStatements`, `Extraction.thrown` and `caught` (copied as static
  methods into `Surround`: `Extraction`'s are bound to its selection).
- refusal: `add_declaration` of `Surround`'s members, for `UnionType` (javax or JDT); the refusal
  suggested `javax.lang.model.type.UnionType` (java first), which was the wrong one here; resent
  with JDT's. The suggestion is a default, but a file importing `org.eclipse.jdt.core.dom`
  classes would have been the better hint (the friction entry of 2026-11-06 on `Pattern`).
- bulk: `Surround`'s members (118 lines, sent twice): new behavior, but `thrown`/`caught`/
  `checked` (about 70 lines) are `Extraction`'s, written again; a static helper both use would
  remove the copy (a follow-up).

## Measured

Through jrefactor: 3 batches (one refused, then resent) and the docs; by hand the goldens
(resources), the roadmap cells by script, this record.

## Assessment

Through the tools; the copy of `Extraction`'s exception walk is the one thing done around them
(no tool turns an instance method bound to a selection into a shared static one in one step;
`move_member` makes it static but keeps the receiver).

## Follow-ups

- One checked-exception walk for `Extraction` and `Surround`.
