---
order: 185
item: 192
status: Done
---

## Item

`move` of a member record whose fields the outer type reads (`r.length`, allowed while nested) was refused by the gate, the fix text saying to rename a clashing type (issue #440, from the Java 17 stress run)

## Status

Done 2026-11-13 (`MoveType.qualifyAndWiden`'s `accessorCall`: a read of a record's field across the new boundary, either way, becomes a call of its accessor when `returnsField` says the accessor is the generated one or only returns the field; otherwise refused naming the field and the accessor. A default to reopen (the smallest, the owner's rule): refuse when the accessor does more, over writing the call anyway and reporting it, and over refusing every such read. `NameCheck` finds a record's field by its component (JDT finds no node for it), so a moved record whose explicit accessor reads its field passes, and skips a package's name, which `package p;` had matched with a local `p`)

## Evidence

The Java 17 stress run, `docs/sessions/stress-enum-switch.md`; `docs/sessions/move-record-fields.md`
