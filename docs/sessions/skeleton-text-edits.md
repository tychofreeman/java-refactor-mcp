# The skeleton rule on every edit that changes a member's body

- **Item:** under skeleton-only, `replace_text`, `replace_expression` and `insert_statements` could
  still write a member a command writes (a constructor that only assigns fields, an override that
  only calls `super` or throws), the gap #271 and #276 closed for `replace_declaration` and
  `replace_statements`. Offered from the follow-ups of `docs/sessions/place-by-kind.md`; the user
  chose all four offered items, this second (2026-11-07). No run has hit it yet.
- **Date:** 2026-11-07
- **Branch:** skeleton-text-edits

## Plan

The change: the skeleton member check runs on what an edit changed, not on what one tool's
arguments are, so every edit that rewrites a member's text is checked the same way.

Derivation:
1. Green: `Generated.checkChanged(before, after, rel, instead)`: parse both texts, match methods
   by their type, name and parameter types, and run `checkMembers` on each method whose text
   changed and that did not already have a refused shape (a stub filled by a tool before stays
   editable).
2. Red: goldens `skeleton_only_refused_text_fills_constructor` (`replace_text` turning a stub
   constructor's body into field assignments), `skeleton_only_refused_expression_super`
   (`replace_expression` of a stub's throw... as needed), `skeleton_only_refused_insert_assigns`
   (`insert_statements` of the assignments into an empty constructor), and an allowed case.
3. Green: call it from `replaceExpression`, `insertStatements` and `Edits.replaceText` for a
   `.java` file, before the gate.
4. Refactor: `replaceStatements`' own check becomes a call of `checkChanged`.
5. Docs: the contract's skeleton refusals, the session notes' Testing line.

## Notes

- read: `Primitives` 27-135 and 196-216, `Edits.replaceText`, `Generated.checkMembers` and
  `onlyAssignsParameters`: where each edit has the text before and after, and what counts as the
  refused shape (one statement per parameter, so a constructor half written is not yet in it).
- bulk: the goldens (67 lines, resources, by shell); `Generated.checkChanged` with `refused` and
  `key` (46 lines), new behavior, through `add_declaration`.
- plan: no allowed golden for `replace_text` alone; `skeleton_only_allowed_edit_beside_assigning`
  covers the rule that matters most, an edit beside a constructor a tool wrote.
- hand: the `replace_expression` golden passed `expression=0`, which the golden loader reads as a
  number ("expression must be a string"), and `"0"` is a string literal; the fixture went to
  `Integer.MAX_VALUE` (two shell edits of resources).

## Measured

From `bin/session-report`: one batch of five steps through jrefactor (the method, three
`insert_statements`, the old check replaced by `replace_statements`), checked at the end, no
refusals; by hand only the record and golden resources; reads: 4 `inspect`.

## Assessment

- **Through the tools or around them:** through them; the Java went in one batch and passed on
  the first run; the only failures were the golden's own argument.
- **Large writes:** `checkChanged` is new behavior.
- **Did the plan hold?** Yes; step 4 (the old check replaced) went in the same batch.
- **Reading:** only the code being changed.
- **One change that would have helped:** the golden loader typing `0` as a number cost two runs;
  `args.properties` could keep a value a schema says is a string as a string. Small; noted here.

## Follow-ups

- `GoldenTest` could type a property by the tool's schema, so `expression=0` stays a string.
