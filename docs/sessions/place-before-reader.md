# A field an edit adds goes before the field that reads it

- **Item:** issue #357 (roadmap #139), from the `convert_loop` work of 2026-11-11 (friction log,
  "add_declaration put a Def after the list that names it"). The user asked for the open issues
  to be fixed (2026-11-11).
- **Date:** 2026-11-11
- **Branch:** place-before-reader

## Plan

The change: a field an edit adds never lands after a field whose initializer reads it. The issue
asked for `Placement.place` to put it before its first reader, but in its own case the reader
names the field only in the batch's next step, so at placement there is no reader yet. The rule
goes where an edit's errors are repaired instead (`Stubs.declare`, which runs for one call and at
a batch's end check): a "Cannot reference a field before it is defined" whose field the change
added (not in the file's text before) moves that field to just before the field that reads it.

Derivation:
1. Red: goldens `add_declaration_field_before_its_reader` (the issue's batch: a static field,
   then the list that names it) and `add_declaration_instance_field_before_its_reader` (one call,
   an instance field an existing initializer reads).
2. Green: `FieldOrder.fix(project, file, old)`, called for each file with errors in each round of
   `Stubs.declare`; a round that moved a field counts as progress.

## Notes

- read: `Placement`, `Stubs.declare`, `Gate.checkedAtEnd` and `Gate.apply`: where an edit's
  errors are repaired, on both paths, and what `old` holds there.
- plan: Placement alone could not fix the issue's case (see the plan); `MemberMove.addTo` is not
  changed: its tools are refactorings, which declare no stubs and so get no repair either.
- bulk: `FieldOrder` (94 lines, `create_file` through `bin/jrefactor`), new behavior.
- The MCP server was disconnected (the default `java` is 25, and the jar's build failed under it);
  every edit went through `bin/jrefactor` with JDK 21 on the PATH.
- plan: the field's blank line, which placement gave it, is dropped when it moves (the goldens
  caught one left behind, then one carried along).

## Measured

From `bin/session-report`: 15 `bin/jrefactor` calls (reads and edits; 3 edit steps counted),
no refusals; by hand only the golden fixtures (resources, 60 lines); one `sed` of a Java file
refused by the read guard, answered with `inspect`.

## Assessment

- **Through the tools or around them:** through `bin/jrefactor`: one batch (`create_file`,
  `replace_text`), one `insert_statements`, two `replace_text`; goldens by shell (resources).
- **Did the plan hold?** Yes, after moving the rule from placement to the repair.
- **One change that would have helped:** none.

## Follow-ups

- The result of `add_declaration` still names the line where the field was placed, not where it
  moved; a later step by that line number would be refused.
