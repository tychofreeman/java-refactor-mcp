# One checked-exception analysis

- **Item:** issue #481 from the 2026-11-13 code review (roadmap row 352, order 342)
- **Date:** 2026-11-14
- **Branch:** fix-checked-exceptions

## Plan

Reproduce the two bugs with goldens (surround around a lambda; extract over a try with
resources). Then: create `Exceptions`, `move` Surround's `checked`, `caught` and `thrown` into it
(their callers follow), rewrite `thrown` as `uncaught(roots)` with the deferred-code skip of
`Extraction.own` and the resources' `close()`, and point Extraction and LoopToStream at it,
deleting their copies with `replace_declaration`. ThrowsCallers only after its owner's PRs.

## Notes

- read: surround refused "Unreachable catch block for IOException"; extract refused "Unhandled
  exception type IOException thrown by automatic close()".
- plan: `create_file`, a `batch` of three `move`s, a `batch` of two `replace_declaration`s and a
  `replace_text`, a `batch` of two rewrites and four safe deletes; all passed first time.
- refusal: none from the tools. My first surround golden wrapped a declaration read after the
  try; the edit declared a field stub for the read (`Callable<String> c; // not implemented`).
  Changed the golden to a statement that declares nothing; the stub is a follow-up.
- hand: golden resource files.

## Measured

Not measured: the session-log hook is not loaded here. Every Java change through `bin/jrefactor`.

## Assessment

Through the tools; the plan held. `ThrowsCallers` is left, since an open PR (#575) changes it.

## Follow-ups

- `replace_statements` `surround` of a declaration that a later statement reads hides the local
  in the block, and the stubs then declare a field for the read: the edit type-checks and means
  something else. Surround could refuse a declaration read after the range.
- `ThrowsCallers` (line 146) can call `Exceptions.checked` once #575 is in.
