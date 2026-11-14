---
order: 183
item: 190
status: Done
---

## Item

`move` of a member type out of a sealed hierarchy whose permits are implicit (the sealed type, or one of its subtypes, out of their shared file) was refused by the gate, the fix text saying to rename a clashing type (issue #441, from the Java 17 stress run)

## Status

Done 2026-11-13 (`MoveType.permits`: each sealed type of the file without a clause whose subtypes the move splits from it gets `permits` with all of them, in the file's order, named from the package as after the move; a private subtype named from the other file is widened to package-private, as other members the move crosses. The gate's fix line for `move` now says to fix what the errors name, a clash being one case. Defaults to reopen (the owner said take the smallest): names from the package (`Options.Debug`) over the shortest name in scope; widening over refusing a private subtype; writing the clause over refusing with the reason. Across packages of a named module is #438's)

## Evidence

The Java 17 stress run, `docs/sessions/stress-enum-switch.md`; `docs/sessions/move-sealed-permits.md`
