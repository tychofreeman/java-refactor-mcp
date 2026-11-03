# File refusals in one place

- **Item:** roadmap #26 (order 19), "Path refusal in one place". From the code
  review of 2026-11-03.
- **Date:** 2026-11-03
- **Branch:** path-refusal-one-place

## Plan

The change, at the highest level: each tool asks `SourceProject` for the file
it edits, and a refusal for a missing or non-Java file has one wording, which
names the Java files in the folder.

Derivation, in order:

1. Choice (asked): the sibling-file hint goes to every Java tool, not only
   `Inspect`. The user chose that.
2. Red: three goldens. `add_parameter` on a missing file (no hint yet),
   `add_parameter` on a non-Java file (refused with other wording), and
   `replace_declaration` on a missing file (no hint yet).
3. Green: `SourceProject.javaFile` and `existingFile`, with `javaFilesHint`
   moved from `Inspect`. Each site calls them; `Declarations.javaFile`,
   `Edits.existing` and the `Extract` and `Signatures` checks are removed.

Behavior changes, model-facing: missing-file refusals in the edit tools name
the Java files in the folder; `Signatures` names a non-Java file as not a
.java file.

## Notes

- `refusal:` the first red run failed on "needs value" for the two
  `add_parameter` goldens: the tool needs `value`. Added `value.txt`, a
  golden-argument fix, not a code change.
- `refusal:` `signature_refused_not_java` was refused already, as "is not a
  Java file under a source root", so it was red on wording, not behavior.
- `hand:` the Java changes were made with Python scripts that replaced anchor
  text, in six files; `replace_text` on those files was the tool that would
  have done it. Same gap as the earlier entries in `docs/friction-log.md`.

## Measured

Not measured (the session log is not recording this session).

## Assessment

Through the tools: the reproduction ran through the golden harness. The edits
were hand edits of the server's own source, as in the earlier items.

Large writes: none; the new methods are about 40 lines, in `SourceProject`.

Reading: the five refusal sites, to compare their wording, and the golden
and test references to "No Java file" (kept for `Inspect`).

One change that would have helped: the same as the earlier items, the repo's
own Java edited through `replace_text`. The friction log names it.

## Follow-ups

- The next review item is #27 (one line helper).
