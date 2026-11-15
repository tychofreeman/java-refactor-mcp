# ExtractType, Behavior and EnumSwitch in parts (#528, item 8, the rest)

- **Item:** issue #528, item 8, what #655 and #657 left: `ExtractType.fields` (213 lines) and
  `components` (226), `Behavior.checkCrossed` (137) and `checkExits` (101), `EnumSwitch.method`
  (134), `ConvertLoop.run`, `MoveToPackage`'s visitor and `MoveMember.run`'s call loop.
- **Date:** 2026-11-15
- **Branch:** item8-rest

## Plan

`extract_function` of each block that takes few of its method's locals, bottom first; skip what
no tool sequence shortens, with a friction-log entry.

## Notes

- ExtractType.fields: `appendConstructor` (4 parameters), `setByConstructor` (1),
  `refuseOutsideUses` (5), `fieldNamed` (2). The third extraction added an import, which moved
  every line down one: the fourth was refused for cutting a statement, and was sent again at the
  new lines (friction log).
- ExtractType.components: `fieldReads` (5), `accessorCalls` (5), `componentTexts` (4), `indices`
  (4; it fills the list declared before it).
- Behavior: `refuseCrossedChange` (4), `writtenKeys` (1), `checkGuardMovedBelow` (5),
  `reportAboveExit` (4). The block that reports a throw past an exit holds a `continue`, so it
  stays.
- EnumSwitch.method: `refuseDeclared` (3). The method text and the enum's own method take 7 to 9
  locals; the case map builds three results.
- Skipped, with a friction entry: `ConvertLoop.run` (already logged in long-methods),
  `MoveToPackage`'s visitor (10+ locals) and `MoveMember.run`'s call loop (the visitor takes 8,
  the per-call loop 9).

## Measured

Not measured (no session-log hook). 13 `extract_function` (one resent); no Java by hand.

## Assessment

Through refactorings. `ExtractType.fields` 213 -> about 155 lines, `components` 226 -> about
177, `checkCrossed` 137 -> about 99, `checkExits` 101 -> about 68, `EnumSwitch.method` 134 ->
about 125.

## Follow-ups

- A context record for MoveMember's and MoveToPackage's call-site loops, if a tool can gather one.
