# SubclassToStrategy.useEdits: the subclass's facts as a record (#528, item 7)

- **Item:** issue #528, item 7 of its last comment: `SubclassToStrategy.useEdits` (11
  parameters) and `constructorEdits` (8).
- **Date:** 2026-11-15
- **Branch:** strategy-context

## Plan

The subclass's facts (`opened`, `subKey`, `subName`, `subConstructors`, `subText`) travel
together: `move_parameter` until they are adjacent, `extract_type` of them into a record, then
`rename` the record and its components.

## Notes

- One batch: `move_parameter` of `project`, `baseName` and `naming`, then `extract_type`
  (`Subclass`, its own file); `rename` of the type by line to `StrategySubclass` (renamed the file).
- refusal: `rename` of `StrategySubclass.subKey` by name: "has no member subKey. Its members are
  none" (a record's components are not members to it); by line: refused, as the accessor calls
  (`sub.subKey()`) were not renamed and would not compile. Components keep their names.
- `replace_text` of the record's `public` and its doc.
- `constructorEdits` (8): its parameters are the superclass's constructors, name and text, the
  edits list, the function's type and two indentations; no group of them is one thing
  `useEdits` also needs. Left.

## Measured

Not measured (no session-log hook). 3 `move_parameter`, 1 `extract_type`, 1 `rename` (2
refused), 1 `replace_text`; no Java by hand.

## Assessment

Through refactorings. `useEdits` 11 -> 7 parameters. The record's component names repeat its
subject (`sub.subName()`), since renaming a component is refused.

## Follow-ups

- `rename` of a record component should rename its accessor and the accessor's calls.
