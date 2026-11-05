# rename of types: captures by nested and outer type names

- **Item:** the follow-up of `docs/sessions/rename-secondary-and-nested.md`: renames
  of types and type parameters are not under `Rename.checkMeanings`, so a type of the
  new name around a use could capture it. The user's choice of 2026-11-05 ("rename of
  types: probe").
- **Date:** 2026-11-05
- **Branch:** rename-type-capture

## Plan

Probes first, as `ProbeTest` cases: a second top-level class renamed to a nested
class's name used where the nested one is in scope; a nested class renamed to the
name of a top-level class used beside it; a type parameter renamed to a class the
body uses. If one changes behavior, bring `renameType` (and `renameTypeParameter`, if
needed) under `checkMeanings`, which compares meanings by binding key and position.

## Results

- **Two type renames changed behavior:** `Item` renamed to `Box` beside a nested
  `Main.Box` made `Item.name()` in `Main` mean the nested class (`item` became
  `nested`); `Main.X` renamed to `Y` made the existing `Y.name()` mean the renamed
  nested class (`yx` became `xx`). Now refused by `checkMeanings`, which `renameType`
  runs as fields and methods do; a top-level type's file moves with it, so the
  meanings recorded before are moved to its new path.
- **A type parameter of a second top-level class** was refused for the file ("the
  file that declares T is not p/Box.java"); `renameTypeParameter` now finds the
  declaring file by the binding, and the probe is refused by its own check ("Item is
  a type that Box already names").

## Notes

- `rename_type` (with its file move and a constructor) was refused by the new check
  at first: JDT's `isDeclaration` is false for a constructor's name, so the renamed
  constructor was not in the renamed set. Found with a temporary debug line
  (`replace_text`, then `undo`); a constructor's name now counts as declaring it.
- The read guard refused a `cat` of a golden's Java through a shell variable path,
  and a trailing `grep` of `Rename.java` (which took the whole command with it);
  read with literal paths and `inspect` instead.
- `hand:` none.

## Measured

All Java through jrefactor (`replace_text`, `undo`); probes by `create_file`.
`ProbeTest`: 65 cases.

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes, with the constructor case found on the way.

## Follow-ups

- None for this item.
