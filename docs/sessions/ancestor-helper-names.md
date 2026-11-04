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

- `read:` `Extract.java` lines 316-326 and `Stubs.java` lines 698-710, to
  confirm where each helper starts. Both as the item says.
- `plan:` the `Extract` name is `ancestorAbove`, not one of the two options
  the plan listed: "above" says the search starts at the parent without
  reading as the parent's own name. The rest held.
- `tool:` both renames went through `bin/jrefactor rename` (the command line,
  since the server's tools were not loaded in this session): `Stubs` first
  (5 occurrences, one file), then `Extract` (4 occurrences, 2 files, the
  `Extraction` callers followed). No Java was written by hand.
- `refusal:` none from the tool. The first `verify` after the renames failed
  in `spotless:check`: the new names changed line lengths. `spotless:apply`
  fixed it, as CLAUDE.md says.

## Measured

Not measured: no session log for this branch (`bin/session-report` found none,
as for the previous item). Counted from the notes: 2 `rename` calls through
`bin/jrefactor`, 0 hand writes of Java, 1 `spotless:apply` run, 0 refusals.

## Assessment

- **Through the tools or around them:** through the tools. Both renames went
  through `rename` (the command line), which reached every call, including
  the callers in another file. The one formatting fix was the formatter, not
  a hand edit.
- **Large writes:** none.
- **Did the plan hold?** Mostly. The operations were the two renames; the
  names differ from the plan's options, and the plan said "or the like".
- **Reading:** two helper bodies, to confirm the start point the item named.
- **One change that would have helped:** `rename` could run `spotless:apply`
  on the files it changes, so a rename that changes line lengths does not
  need a second step. Small; not put on the roadmap yet.

## Follow-ups

- The `ancestor` item is done. The remaining candidate is layering (roadmap
  #39), after this one.
