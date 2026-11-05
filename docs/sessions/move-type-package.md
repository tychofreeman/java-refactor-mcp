# move_type to another package (roadmap #80)

- **Item:** roadmap #80, the first of the user's 2026-11-04 list: move a
  top-level type to another package. Friction log: "No tool moves a type or
  ..."; types and fixtures moved by hand.
- **Date:** 2026-11-04
- **Branch:** move-type-package

## Decisions (this session; the user agreed to the recommendations and asked for the list)

- **An option, not a tool:** `move_type` gains `package`. With it, `name` is
  a top-level type; without it, a nested one, as now. CLAUDE.md prefers an
  option on an existing tool, and both move a type to a file of its own.
- **First version:** the file's only top-level type, out of a named package,
  into the same source root. A file with several top-level types is refused
  (move the others first); the default package is refused (nothing can import
  from it).
- **Widen, as `move_type` does:** what the move puts across a package
  boundary while package-private becomes `public`, named in the result:
  the type, and its members used from another package; and the declarations
  of the old package the type uses. Protected members used by a class that is
  not a subclass are left to the gate.

## Plan

The change: the type's file moves to the new package's directory with its
package line changed; every import and qualified name of it follows; files
of its old package import it; and the move's new package boundary is
widened where it is crossed.

1. Red: goldens `move_type_to_package` (a used package-private helper and
   member, an importing file, a fully qualified name, a same-package user),
   `move_type_to_package_refused_exists`,
   `move_type_to_package_refused_two_types`, by `create_file`.
2. Green: `create_file` of `MoveToPackage` (new behavior, about 250 lines),
   and in `Tools` the `package` property and the dispatch by `replace_text`.
   `MoveType`'s top-level refusal names the option.

## Notes

- `read:` `MoveType.run` (lines 52-233), for what to reuse (`Holding`,
  `Imports.adding`, `Imports.fix`, the gate with a null text to delete).
- `bulk:` `MoveToPackage`, 342 lines by `create_file`: new behavior.
- The three goldens were red as an unknown argument (`package`), not run
  before the code; the first run after it passed all three, `result.txt`
  included.
- Probe through `GateCostTest` on Commons Lang: moving
  `org.apache.commons.lang3.arch.Processor` to `...archx` applied (15
  references, 1.3 s) and was undone. Moving `CharUtils` to `...chars` was
  refused by the gate: `StringEscapeUtils`, of the old package, names
  `CharUtils` only as a qualifier (`CharUtils.LF`), which the first version
  did not count as a use, so the file got no import. Golden
  `move_type_to_package_static_use` red, then one `replace_text`; the probe
  then applied (14 references in 4 files, 2.5 s).
- The result counted lines as references; now each reference counts, as
  `move_type` counts them.

## Measured

From `bin/session-report` (before the docs edits):

- tool calls: 24
- through jrefactor: 31 edit steps
- refused by jrefactor: 0
- by hand: 0 Java; the record and the docs by Write, Python and `sed`
- large writes: 1 (`MoveToPackage`, new)
- reads: Bash java 4, inspect 1

## Assessment

- **Through the tools or around them:** through.
- **Large writes:** `MoveToPackage` is new behavior; it shares no code with
  `MoveType`'s nested move beyond the helpers it calls.
- **Did the plan hold?** Yes; the probe added a case the goldens lacked.
- **One change that would have helped:** none.

## Follow-ups

- A type in a file with other top-level types; a type in the default
  package; another source root.
- Protected members used by a class of the old package that is not a
  subclass: left to the gate.
- Javadoc `{@link a.Shop}` in other files is not rewritten.
