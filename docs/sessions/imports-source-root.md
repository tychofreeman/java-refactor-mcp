# Import fix only under a source root

- **Item:** the friction-log entry of 2026-11-03 (`create_file` of a golden's
  expected file under no source root was refused: "Line could be ..."), which
  had no roadmap row. Chosen with "Do all three in order" (the second of three).
- **Date:** 2026-11-03
- **Branch:** imports-source-root

## Plan

Make the import fix depend on where the file is: `Edits.gated` fixes imports
only for a file with a package under a source root (`SourceProject.packageOf`).
Elsewhere the text is applied as written; the gate counts errors only under a
source root, so it needs nothing from the imports there. Operations: a red
unit test `EditsTest` for both cases (outside: no refusal, text unchanged;
under a source root: still refused), then the one-line branch in `gated`.

## Notes

- `plan:` the test's first try used `List`, which the index does not see as
  ambiguous (`java.awt` is discouraged), so the control did not refuse. `Timer`
  (`java.util` and `javax.management.timer`) is ambiguous; the control refuses.
- `read:` `Edits.write`, `Imports.fix`, `SourceProject.packageOf`, golden layout
  (`GoldenTest`): to see where the refusal came from and how a non-Maven project
  gets its source root.
- `hand:` none in the code. `EditsTest.java` was written with `create_file`,
  its setup fixed with `replace_text`, and `Edits.gated` changed with
  `replace_text`. The test's setup needed a `src` directory (JDT refuses a
  source path that does not exist), found by the first red run.

## Measured

Not measured: no session log for this branch.

## Assessment

Through the tools: the test and both source edits went through
`bin/jrefactor`. Two reads were needed to plan (the import fix and the
package lookup); no body was read to find uses. Two plan misses, both in the
test's fixture: an ambiguous name that is not ambiguous in this index, and a
missing directory. Each showed in one red run and cost no code change.
The idea in the friction entry also named type-checking only under a source
root; the gate already counted only there, so no change was needed for it.

## Follow-ups

- A golden case with a Maven project (a file outside `src/main/java`) would
  check the same path end to end; the unit test covers the rule.
