---
order: 212
item: 220
status: Done
---

## Item

`remove_else` moved the else block's locals into the enclosing block, where a later statement that read a field of the same name then read the local (it type-checked); a tab-indented else body was dedented by the wrong amount

## Status

Done 2026-11-13: `RemoveElse.namedAfter` refuses when a local or local class the else block declares is named by a statement after the if (by identifier, as `ConvertLoop.namedAfter`), naming `rename` as the fix; `indentWidth` counts tabs as `leadingWhitespace` does. Default to reopen (the owner said take the smallest): refuse, over keeping the else's statements in a bare block and over comparing by binding (a qualified `p.name` after the if is refused too)

## Evidence

Issue #471 (code review); probe `remove_else_local_hides_field`
