---
order: 171
item: 178
status: Done
---

## Item

`inline` of a local declared with an array initializer (`String[] names = {"a", "b"};`) wrote `{"a", "b"}.length`, and the refusal blamed the user's file as if it did not parse (issue #410)

## Status

Done 2026-11-13 (`InlineVariable`: `new` and the declared type, its dimensions after the name included, before the initializer, in parentheses where indexed; `Edits.resultParses`: a refactoring's own output that does not parse is refused as the tool's fault, naming the file relative to the root. An array creation, `new int[n]` or an initializer alone, used in several places is refused as a new object is (`Effects.newArraysNow`, `Behavior.flow`), a default to reopen over reporting it: it had been inlined silently, each use a different array)

## Evidence

The Commons Lang stress run, `docs/sessions/stress-new-options.md`; `docs/sessions/fix-inline-array-initializer.md`
