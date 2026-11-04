# Stubs keep wildcard types, as Extract does

- **Item:** order 43 (row 36), the wildcard part: `Stubs.typeName` erases a
  wildcard argument to its raw type, `Extract.typeName` keeps it. The user chose
  to keep wildcards in stubs (2026-11-04), the option that matches `Extract`.
- **Date:** 2026-11-04
- **Branch:** stubs-keep-wildcards

## Plan

The change, at the highest level: a stub's parameter type is the type the
caller wrote, wildcards included; only captures, which cannot be written, are
erased.

Operations, in order:

1. Red: a golden, `stub_wildcard_argument`: `add_declaration` of a test that
   calls `process(xs)` where `xs` is `List<? extends Number>` and `process` is
   undefined. The stub should read `process(List<? extends Number> xs)`.
2. Green: `Stubs.typeName` returns a wildcard as `Extract.typeName` does (`?`,
   `? extends T`, `? super T`), and erases only captures.
3. The check: the existing `stubs_*` goldens (their text must not change) and
   `./mvnw verify`.

## Notes

- `red:` `stub_wildcard_argument` failed for the planned reason: the stub came out
  as `process(List<Number>)` and the gate refused the call, "not applicable for the
  arguments (List<capture#1-of ? extends Number>)".
- `hand:` the change to `Stubs.typeName` was a Python exact-string replacement. Tool
  that fits: `replace_declaration` for `typeName`. Not tried: the jrefactor tools were
  not loaded.
- `refusal:` none beyond the red golden above.
- `plan:` the plan said wildcards only. The failing case needed one more step: the
  argument's type is a capture, so the capture's wildcard (`getWildcard`) is written.

## Measured

Not measured: no session log for this branch. Counted from the notes: 1 hand edit
(`typeName`), 1 new golden, 1 red run, 0 refactoring calls.

## Assessment

- **Through the tools or around them:** the golden was written by hand; the one edit
  was by Python replacement, though the tool for it exists.
- **Did the plan hold?** Mostly. The plan missed that a call's argument has a capture
  type, which the red golden showed. The fix covers it.

## Follow-ups

- Captures still differ between the two helpers: `Stubs` writes the wildcard a
  capture came from; `Extract` erases captures, since its parameters are declared
  types, not captures. That difference is intended.
