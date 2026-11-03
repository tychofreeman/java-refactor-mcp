# One ASTParser configuration

- **Item:** the review item "One ASTParser configuration" (`#` 28; order 21):
  `Declarations.members` set its own parser options and no environment, and
  `Jdt` set its own; `Jdt` should own every parser, so the options cannot
  drift.
- **Date:** 2026-11-03
- **Branch:** one-parser-config

## Plan

One place sets the parser options: `Jdt`. The change is: `Jdt.options()`
(the options for `SOURCE_LEVEL`) and `Jdt.syntaxParser(kind, source)`, which
both `parseSyntax` and the new `Jdt.parseMembers` use; `Declarations.members`
calls `parseMembers` and loses its parser.

Operations, in order:
1. Read the parser sites (grep for `ASTParser`): `Jdt` (three) and
   `Declarations.members` (one).
2. Move `Declarations`'s members parse into `Jdt.parseMembers`, using the one
   options function.
3. Make `parseSyntax` and `parseMembers` share `syntaxParser`; make the
   bindings parser use `options()`.
4. Remove the three `options.put` calls in the bindings parser: they set the
   source, compliance and target keys that `setComplianceOptions` already
   sets.

The one edit that could change behavior is step 4. The tests check it.

## Notes

- `read:` the `Jdt` parser code and `Declarations.members`, to compare the
  options. The two options sets differ only in those three puts.
- `hand:` `Jdt.java` and `Declarations.java` changed with the Edit tool. The
  java-refactor-mcp tools were not in this session's tool list. A
  `replace_text` would have made each edit (one per file, two edits in
  `Jdt`). Logged in the friction log.
- `refusal:` none.
- `plan:` the plan was written before the edits and kept.

## Measured

Not measured. No session log for this branch. The change is behavior-neutral:
`./mvnw verify` ran 142 tests with 0 failures (2 opt-in cost tests skipped),
including the golden cases for `replace_declaration`, `add_declaration`
and the `parseSyntax` callers.

## Assessment

- **Through the tools or around them:** around. Two Edit-tool edits in
  `Jdt.java` and one in `Declarations.java`, plus the import tidy that the
  formatter did. `replace_text` covers each one.
- **Large writes:** none. The new code is about 20 lines, all new behavior.
- **Did the plan hold?** Yes. Step 4 needed no test change.
- **Red test:** none. A pure refactor of options setup; the change is
  behavior-neutral, so the golden cases are the guard, not a new red test.
- **One change that would have helped:** the java-refactor-mcp tools in the
  session, so the edits went through `replace_text`.

## Follow-ups

- No more parser sites: `grep ASTParser` finds `Jdt` only.
