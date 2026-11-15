# rename of a record component (issue #659)

- **Item:** issue #659, roadmap 516, the owner's third of three move/rename bugs
- **Date:** 2026-11-15
- **Branch:** rename-record-component

## Plan

A record component is one name in four places: the component, its private field, its accessor and
the canonical constructor's parameter. Map every binding to the component's field
(`componentField`) and rename each `SimpleName` that maps to it, through `renameUses` as the field
and method renames do; by name, find the component in the record header before
`Declarations.find`. Then rename `StrategySubclass`'s components through the new rename.

## Notes

- read: `Rename` outline, `run` (both), the field and method renames, `renameUses`, `checkMeanings`.
- plan: goldens first, all six red: by name "has no member subKey"; by line the gate's "would add compile errors".
- read: JDT gives the component's own name a binding with `isRecordComponent()` and no declaring class (a scratch program over the golden, /tmp, not committed); hence `componentAt`.
- refusal: `checkMeanings` refused the new names written at the field and accessor uses: they have no declaring name in the source. Added `renamedToo` with `add_parameter` (value `b -> false` at the four other calls).
- refusal: renaming `StrategySubclass.subText` to `text`: the capture check took `sub.subText()` for a field read a local `text` would capture. Accessor calls are now skipped by that check; the batch then renamed all four.
- Java written through: `batch` x4 (add_declaration, replace_text, add_parameter, rename), `replace_text` x1. No hand edit of Java.

## Measured

Not measured (no session-log hook here); 6 tool calls that wrote Java, one refused by my new check (a bug in it, fixed).

## Assessment

Through the tools, and the new rename was used on the project's own record at the end.

## Follow-ups

- A record component whose accessor implements an interface method is renamed without the interface's method; the gate refuses it. Renaming the interface's method with it would follow the method family; not built.
