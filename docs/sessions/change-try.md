# change_try: change a try that is there

- **Item:** issue #364 (roadmap row added with it), the third of the four items the user chose
  on 2026-11-13. The user chose, from options: a new tool `change_try` (over options on
  `replace_statements` and over two tools), every change but resources in the first PR (over
  catch clauses only and over everything), and an edit (over a refactoring that reports through
  `Exceptional.change`).
- **Date:** 2026-11-13
- **Branch:** change-try

## Plan

A try is found by the line it starts on, as `add_case` finds a switch; one change a call, written
as text edits on the original and passed through `Edits.gated`, as the edit primitives are.

- `add_catch` (a type), with `body` (default `throw e;`, as `surround=try` writes) or `join` (an
  existing clause's type, to make it a multi-catch): placed before the first clause that catches
  a supertype; refused when a clause already catches the type. The type's superclasses come from
  a probe field, as `ThrowsCallers.supersOf` resolves one.
- `remove_catch` (a type): the clause, or the type alone from a multi-catch.
- `finally` (statements), `remove_finally`.
- `unwrap`: the body's statements in the try's place; refused with resources or a finally.
- A try left with no catch, finally or resources is unwrapped, and the result says so.
- What the gate catches stays its own: an unreachable or never-thrown catch, an exception left
  unhandled.

Derivation: make the change easy (`Primitives.gated`, `ThrowsCallers.freeIn` and `step` become
package-private); red golden cases; `ChangeTry` and its `Def`; then fold `supersOf` onto the new
resolver if they come out alike.

## Notes

- read: `Primitives.addCase`, `gated`, `indent`, `Opened.startingOn`, `Surround`,
  `ThrowsCallers.catches`, `supersOf`, `clause`, `freeIn`, `step`, `Args`: the pattern of an edit
  found by line, and what could be reused.
- hand: the twelve golden cases by a Python script (fixtures under `resources/`).
- bulk: `ChangeTry` (321 lines) by `create_file`: new behavior, except `supersOf`, a copy of
  `ThrowsCallers.supersOf` made static; folded back in the refactor step.
- refusal: the refactor batch deleted `ChangeTry.supersOf` before changing its call ("is used in
  ... line 69"); `retry` with `remove` and `after` moved the delete last, and it passed.
- plan: `Primitives.line` became package-private too.

## Measured

Not from `bin/session-report` (its log mixes this session's branches); from the notes: two
`batch`es and one retry, one `create_file`, one `replace_text`-only batch, through jrefactor;
one refusal, answered by `retry`; about a dozen `inspect` reads; goldens and docs by hand.

## Assessment

- **Through the tools:** every Java change; the one refusal was answered by reordering with
  `retry`, not by hand.
- **Large writes:** `ChangeTry` was new behavior written whole; its copied resolver was the one
  piece of existing code typed again, and the refactor step folded it into
  `ThrowsCallers.exceptionSupers`, by `replace_declaration` rather than `extract_function`
  (the instance method's body read `family` and `project`).
- **Did the plan hold?** Yes; all twelve goldens passed on the first run of the new tool.
- **One change that would have helped:** none new; copying a golden case is logged.

## Follow-ups

- Resources of a try-with-resources (add or remove one), the issue's later step.
- An A/B of tasks that change a try, to see whether models find `change_try` by name.
