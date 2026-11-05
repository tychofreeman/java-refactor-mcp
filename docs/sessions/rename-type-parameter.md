# rename renames type parameters (roadmap #84, first part)

- **Item:** roadmap #84, widening existing tools; this is its first part:
  `rename` of a type parameter, which it refused ("not built yet", seen in the
  stress runs). The other parts (`inline_function` for instance methods and
  longer bodies; `extract_method` with several results) are their own PRs.
- **Date:** 2026-11-04
- **Branch:** rename-type-parameter

## Plan

A type parameter's uses are all inside the class or method that declares
it, so the rename is one file: every name bound to it in that declaration,
and `@param <T>` in its Javadoc. A new name that the declaration already uses
for a type is refused: the parameter would hide it (and the code could still
compile).

1. Red: goldens `rename_type_parameter`, `rename_type_parameter_refused_clash`.
2. Green: `Rename.renameTypeParameter` by `add_declaration`, and its call in
   place of the refusal by `replace_text`.

## Notes

- `read:` `Rename.renameType`'s head, `summary`, `gate` and `apply`, to reuse them.
- `plan:` the first golden put `@param <T>` mid-line in its Javadoc, where it is
  text, not a tag; the golden was wrong, and was fixed (a line of its own).
- Both red first, green after.

## Measured

From `bin/session-report` (before the docs edits): all Java through jrefactor
(`create_file` for the goldens, one `replace_text`, one `add_declaration`, three
`replace_text` for the golden fix), no refusals.

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes.

## Follow-ups

- A type parameter of a generic method used in a lambda's explicit types is
  covered (it is a name in the method); one shadowed by an inner class's own
  type parameter of the same name is not tested.
