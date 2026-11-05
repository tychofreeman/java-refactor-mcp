# rename: second top-level classes, and captures by nested and inherited names

- **Item:** two `rename` gaps from `docs/sessions/probes-remaining.md`: a member of a
  second top-level class of a file was refused as "not in this project", and an inner
  class's declaration of the new name could capture a use (not probed). The user's
  choice of 2026-11-05 ("Do items 1, 2, and 3").
- **Date:** 2026-11-05
- **Branch:** rename-secondary-and-nested

## Decisions (this session)

- **`Signatures.projects`** accepts a type whose binding is from source
  (`ITypeBinding.isFromSource`; the parse reads only the project's source roots), not
  only one in the file its name implies. Every tool that asks it gains second
  top-level classes.
- **That uncovered three captures** the gap had hidden, each applied and changing the
  output: a field a subclass hides, an outer field read in an inner class that has a
  field of the new name, an outer method called bare in an inner class that has a
  method of the new name. `rename`'s checks were by the declaring type and locals only.
- **One check for all of them, after the write:** `Rename.checkMeanings` parses the
  changed files and every file holding the new name, maps each occurrence of the new
  name back through the edits, and refuses when a renamed use does not mean a renamed
  declaration, or an existing use of the new name means something else; the refusal
  puts the files back (`rename_refused_nested_capture` pins that the tree is
  unchanged). It covers fields and methods.

## Notes

- Probes first: `rename_secondary_class_field` (applies now), and
  `rename_nested_capture_field` and `_method`; with the gap fixed, those two and
  #195's `rename_field_hidden_by_subclass` failed as "behavior changed", then
  refused with the check. `rename_method_into_override` stays refused, by #195's
  overload rule.
- My golden's fragment had line 9 for line 10.
- `hand:` none.

## Measured

All Java through jrefactor (`replace_text`, `add_declaration`); probes and the golden
by `create_file`. `ProbeTest`: 62 cases.

## Follow-ups

- Types and type parameters are not under `checkMeanings` (a nested type of the new
  name could capture a renamed type's use); not probed.
