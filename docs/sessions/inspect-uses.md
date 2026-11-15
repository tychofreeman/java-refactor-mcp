# inspect uses

- **Item:** issue #369 (parity with gorefactor's `inspect`), its first part, `uses`; roadmap #504
- **Date:** 2026-11-15
- **Branch:** inspect-uses

## Plan

A query, no refactoring: a new class `Uses` (one `Holding` parse, declaration by label, uses by
binding key, roles from the parent node), an `uses` argument on `inspect` routed before `names`.
gorefactor's `internal/inspect/uses.go` gives the roles, their order and the format.

## Notes

- plan: golden `inspect_uses` written first, with the expected text of four names; red
  ("inspect does not take uses").
- bulk: `create_file` of `Uses.java` (471 lines) from a draft in the scratchpad; one batch of three
  `replace_text` steps in `Tools`.
- refusal: a `replace_text` whose `new` closed one parenthesis too many ("does not parse"); fixed
  in the batch file, sent again.
- refusal (test): `definitionsStaySmall`, inspect 2129 characters against 1700, then 1748; the
  description and two argument texts shortened.
- hand: none in Java.

## Measured

Not measured (no session-log hook here). Java changes: 6 tool calls (`create_file`, 2 batches,
3 `replace_text`), none by hand.

## Assessment

Through the tools. The golden passed on the first run of the code.

## Follow-ups

- `function`, `targets` and the body summary, the rest of #369.
- A local variable or parameter by name (gorefactor takes package-level names and members only).
