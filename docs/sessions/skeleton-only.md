# Skeleton-only creation, an experiment flag

- **Item:** roadmap #116, the last part of the constructive-transformations slice
  (`docs/design.md`, "Constructive transformations").
- **Date:** 2026-11-05
- **Branch:** skeleton-only

## Plan

With the flag on, a Java file can only start as a type's header: `create_file` with
`content` whose types declare members (methods, fields, constructors, nested types) is
refused, naming `declaration`, `test_for` and `add_declaration`. A header, an enum's
constants and a record's components pass; `declaration` and `test_for` pass (their
members are generated). Non-Java files are not touched. Off by default.

Derivation:

1. Goldens first (red): a refused class with a method, an allowed header, an allowed
   `test_for`, each with `skeleton_only=true`, which `GoldenTest` sets on the session.
2. `Session.skeletonOnly`, from `JREFACTOR_SKELETON_ONLY=1` (`add_declaration`).
3. The check in `Edits.createFile`, before the write (`replace_text`).
4. Docs: the description, the contract, the README's experiment note, the roadmap, the
   session notes; the A/B arm itself is left for when the user asks for a run.

## Notes

- read: `Session`'s fields, and where the code reads the environment (`getenv`), to
  follow its style.
- hand: three goldens' fixtures by heredoc (under `resources/`).
- Nothing refused; the change was one batch of four edit steps and one description edit.

## Measured

Through jrefactor: 5 edit steps (one batch of four, one `replace_text`); no refusals;
no Java by hand.

## Assessment

Through the tools; small, as planned. The flag is a refusal (a mechanism), and its
description and the refusal name the constructive operations to use instead.

## Follow-ups

- The A/B arm: an `-mcp` wrapper with `JREFACTOR_SKELETON_ONLY=1`, on feature tasks
  (gorefactor's `ab-features`, ported); costs money, so it waits for the user's yes.
- Next constructive operations, by what this slice's use logs: implement/override on an
  existing type, a constructor from fields, surround with.
