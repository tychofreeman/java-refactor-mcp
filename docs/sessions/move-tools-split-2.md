# InlineClass.run and MoveToPackage.run in parts (#528, item 6, part 2)

- **Item:** issue #528, item 6 of its last comment, second part: `InlineClass.run` (189 lines)
  and `MoveToPackage.run` (185 lines).
- **Date:** 2026-11-15
- **Branch:** move-tools-split-2

## Plan

`extract_function` of the blocks that do one job each, bottom first so the line numbers of one
read hold: InlineClass's member text, use edits, name clashes, the field's declaration in the
second parse and the field lookup; MoveToPackage's package line and the result's reference list.

## Notes

- InlineClass: `body` (3 parameters), `useEdits` (4), `refuseClashes` (4), `homeDeclaration` (2),
  `fieldNamed` (2); then `inline` of the temporaries `fd` and `hd` the extractions left
  (`FieldDeclaration decl = fd;`).
- MoveToPackage: `withReferences` (3), `packageLine` (4). Its visitor and its per-file loop read
  ten or more locals each; extracted, they would take that many parameters. A record of the move
  (`key`, `old`, `pkg`, `oldName`, `newName`, `root`, `target`) would come first.

## Measured

Not measured (no session-log hook). 7 `extract_function`, 2 `inline`; no Java by hand.

## Assessment

Through refactorings. `InlineClass.run` 189 -> about 97 lines; `MoveToPackage.run` 185 -> about
160.

## Follow-ups

- `MoveToPackage`: a record of the move's names, then its visitor and per-file loop as methods.
