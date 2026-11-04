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

