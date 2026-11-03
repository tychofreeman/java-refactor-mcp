# create_file fixes imports before the gate

- **Item:** roadmap #35 (order 28), "`create_file` fixes imports before the
  gate, as every other edit does". From the full code review of 2026-11-03,
  read, not run; this item reproduces it first.
- **Date:** 2026-11-03
- **Branch:** create-file-imports

## Plan

The change, at the highest level: a new Java file gets the imports it needs
the same way every other edit gives them, so a library type is imported, not
declared as a project class.

Derivation, in order:

1. Red: a golden case `create_file_java_imports`. A new `com/example/B.java`
   uses `List<String>` with no import. Expect `out/` to hold the import and no
   `List.java` in the project. Write `result.txt` from the tool's output once
   the behavior is right. This is the measurement: the case fails today, and
   the failure shows the stub it declares.
2. Green: `Edits.createFile` runs the existing `write` helper for Java files
   (it fixes imports against the old text, then runs the gate). The result
   text gets the same `imported ...` suffix the other edits give.
3. Refactor while green: `createFile` no longer has its own gate call, so the
   text-and-gate step is in one place (the `write` helper).

Behavior change, model-facing: a new Java file's result text gains
"; imported ..." when it imports a name, and a file that used an unimported
library name is no longer given a project stub.

## Notes

## Measured

Not measured (the session log is not recording this session).

## Assessment

## Follow-ups
