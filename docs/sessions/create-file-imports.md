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

- `hand:` `Edits.java` changed by a Python script that replaced anchor text
  (the `write` call, the createFile body, the result suffix). Behavior change
  in an existing edit, about 30 lines. The tool that would have done it is
  none: this is the edit tool's own code, so there is no
  java-refactor-mcp operation to move it. Written by hand as the session
  started, before the golden case passed.
- `refusal:` the red golden was refused with "The type List is not generic; it
  cannot be parameterized with arguments <String>". That is the reproduction,
  not a refusal to answer.
- `hand:` the first `content.txt` held only a member, not a class; the
  refusal was "does not parse ... Compact Source Files". The golden was wrong,
  not the tool. Fixed by writing a full class.
- `hand:` `result.txt` had a trailing newline the tool's text does not have.
  Fixed by writing the file without it.
- `read:` `Edits.java` and `GoldenTest.java` in full, to plan the change and
  see how `write` and the result are shaped.

## Measured

Not measured (the session log is not recording this session).

## Assessment

Through the tools: the reproduction was a golden case run through the golden
harness, as the record's plan asked. The green change was a hand edit of
`Edits.java` (the edit tools cannot edit their own source), so it went around
the tools in the way CLAUDE.md allows for edits no tool covers. The plan held:
the operation was "route the create through `write`", and no body was
rewritten. The fix reuses `write`, so the text-and-gate step is in one place.

Large writes: none. The golden case files are fixtures, not Java behavior.

Reading: `Edits.java` and `GoldenTest.java` in full. The read of `Edits.java`
was needed to find the anchors for the hand edit; a tool that edited the
createFile body by name would have removed that read.

One change that would have helped: a tool or option that edits a method body
by its name in a file (`replace_declaration` does this for Java members, but
was not used on the tool's own source). Not built; the source of the edit
tools is not a case the tools need to cover.

## Follow-ups

- #36 (generic overload split), #37 (`Stubs.uses` import picks) and the rest
  of the review items remain open on the roadmap.

