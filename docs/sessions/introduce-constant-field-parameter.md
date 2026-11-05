# Introduce constant, field and parameter (roadmap #86)

- **Item:** roadmap #86: `extract_variable`'s expression as a `static final`
  field, an instance field, or a new parameter of the enclosing method. The
  user's list of 2026-11-04.
- **Date:** 2026-11-04
- **Branch:** introduce-constant-field-parameter

## Decisions (this session)

- **An option on `extract_variable`, `as`,** over three tools: the expression
  is found the same way, and only what it becomes differs.
- **Literals and constants only** (static final fields): an expression with a
  local, a parameter, an instance field or a call would be evaluated once, at
  initialization (a field), or in the caller (a parameter), not where it was.
- **A field is `private final`, a constant `private static final`,** declared
  as the type's first member; a field is refused in a static member.
- **A parameter is `add_parameter`** (every call passes the expression), then the
  expression is replaced by the name; composed in one call.

## Plan

1. Red: goldens `extract_variable_as_constant`, `_as_field`, `_as_parameter`,
   `_as_constant_refused_local`.
2. Green: `as` on `ExtractVariable.run` (`add_parameter`, which also changed the
   call in `Tools`), the hand-off to a new `Introduce` (`create_file`), and the
   schema (`replace_text`).

## Notes

- `refusal:` the first batch ended in a `replace_text` of `Tools` that I meant as
  a no-op and whose `old` did not match; the batch was sent again without it.
  `add_parameter` had already updated the call in `Tools`.
- All four goldens passed on the first run after the change.

## Measured

From `bin/session-report` (before the docs edits): 9 tool calls; 1 refactoring
step (`add_parameter`), 17 edit steps; 1 refusal (above); no Java by hand; 1
large write (`Introduce`, new).

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes.

## Follow-ups

- Replacing every occurrence of the expression in the type (`all_occurrences`).
- A field that the constructor initializes from an expression of parameters.
