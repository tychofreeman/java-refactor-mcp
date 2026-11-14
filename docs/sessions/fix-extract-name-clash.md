# Extract's name-clash checks walk enclosing types

- **Item:** issue #469 (code review, extract-inline #1): `extract_function` and `extract_variable`
  check a new name only against the type and its supertypes, so a name an enclosing type, an enum,
  a record or an interface declares is captured silently.
- **Date:** 2026-11-13
- **Branch:** fix-extract-name-clash

## Plan

The change: the clash checks see every declaration of the name that unqualified code at the
extraction can reach. Derivation: probes first (one per shape in the issue), then one helper in
`Extraction` that walks the enclosing types' ancestors, called from `refuseClash` and
`checkOverride`; `ExtractVariable.checkFieldName` walks every enclosing type (any
`AbstractTypeDeclaration` or anonymous class) and each one's superclasses and interfaces. The
smallest fix the issue names, not the general meaning check (which would compare every name of the
enclosing type before and after).

## Notes

- read: `inspect` with `names` for `Extraction.refuseClash`, `checkOverride`, `ancestors` and
  `ExtractVariable.checkFieldName`; `pattern` for their callers.
- plan: five probes (outer method, outer field, enum field, interface constant, record field)
  each applied and changed the program's output before the fix; a sixth with `visibility=package`.
- refusal: `inspect` with `lines` (it takes `start_line`/`end_line`); resent.
- `Extraction`: one `batch` (`add_declaration` of `refuseEnclosing`, two `replace_text` calls
  adding it to `refuseClash` and `checkOverride`); `ExtractVariable`: `replace_declaration` of
  `checkFieldName` (imports fixed by the tool).
- hand: golden and probe resource files, and the docs (plain files, as the brief says).

## Measured

Not measured: the session-log hook is not loaded here. Three tool calls wrote all the Java.

## Assessment

Through the tools: every Java change went through `bin/jrefactor`, and reading through `inspect`.
The plan held. The smallest fix was taken over a meaning check (Decisions, default to reopen).

## Follow-ups

- A local of an enclosing method that a new local in an anonymous or local class's method would
  hide, and a static import of the name, are still not checked (Decisions).
