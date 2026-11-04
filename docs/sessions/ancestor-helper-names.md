# Name the ancestor helpers by where they start

- **Item:** roadmap #22's open naming: `Extract` and `Stubs` each have a helper
  called `ancestor`, and the two start at different nodes (the parent in
  `Extract`, the node itself in `Stubs`). Chosen by the user from `what's next?`,
  2026-11-04, as the second of the remaining candidates.
- **Date:** 2026-11-04
- **Branch:** ancestor-helper-names

## Plan

The change, at the highest level: a helper's name says where its search
starts, so two helpers with different starts cannot be read as one.

Operations, in order:

1. Rename the `Stubs` helper to name that it starts at the node itself
   (`ancestorOrSelf`), with `rename` on its declaration; every call follows.
2. Rename the `Extract` helper to name that it starts at the parent
   (`ancestorOfParent` or the like), with `rename`.
3. No behaviour change, so no golden; the existing goldens and `./mvnw verify`
   are the check. The one edit that matters is the rename itself.

Hypothesis: both are private helpers in one package, so each rename is one
file-local change.

## Notes

