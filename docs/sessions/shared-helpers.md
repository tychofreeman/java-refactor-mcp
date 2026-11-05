# Shared helpers instead of copies (roadmap #98)

- **Item:** roadmap #98, from PR #161's review: `lineOf`/`lineStart`,
  `indentOf`, `within` and node-end copies, and a query for "find nodes like
  this". The user's order of 2026-11-04 ("the items introduced by PR #161").
- **Date:** 2026-11-05
- **Branch:** shared-helpers

## Plan

Each copy moves to one home by expand, migrate, contract: declare the shared
helper (`create_file` of `Nodes`, `add_declaration` in `Lines`), make every copy
delegate to it (`replace_declaration`, one batch, check `end`), then fold each
delegate into its callers (`inline_function`, one batch). No tool moves a static
method to a class of another file (`move_member` is within a file), so the
shared helper is declared, not moved.

## Decisions (this session)

- **Homes:** `Nodes` (new) for `end(node)` (four copies: `Signatures.end`,
  `MemberMove.end`, `Headers.end`, `Extract.endOf`) and `within` (two);
  `Lines.offsetOf` (two `lineStart` copies by text) and `Lines.indentOf` (three).
  `Declarations.end` and `Inspect.end` (the last character, `- 1`) and
  `Primitives.lineStart` (by the unit) mean other things and stay.
- `InlineVariable.lineStart` did not refuse a line past the end; through
  `Lines.offsetOf` it now does.
- **The query:** `Nodes.all(root, type)`; seven visitors that only collected
  became loops (`ConvertLambda`, `Extraction`, `InlineFunction`, two in
  `InlineVariable`, `Imports`, `MoveMember`). The other 22 single-type
  visitors that never prune change when their classes next change, as the row
  allows; visitors that prune (return false) stay visitors.

## Notes

- `refusal:` `inline_function` of `Signatures.end` refused a `SimpleName`
  argument for `ASTNode` (#78's rule); answered by widening the tool in its own
  PR (#169, `docs/sessions/inline-subtype-argument.md`).
- `refusal:` then `NameCheck` refused a capture at `ExtractStruct` that was
  not one (anonymous-class keys hold positions); fixed in its own PR (#170,
  `docs/sessions/namecheck-positions.md`).
- The fold batch then ran through `bin/jrefactor` (the session's MCP server is
  the jar from its start): 13 steps, 80 calls.
- `hand:` none in Java; the batch JSON was written by a script.

## Measured

All Java through jrefactor: one `create_file`, 14 `replace_declaration` and
`add_declaration` steps, 13 `inline_function` steps, 7 `replace_text` steps for
the visitors. Two refusals, each answered by fixing the tool. 20 files, net 65
lines fewer.

## Assessment

- **Through the tools or around them:** through; both refusals were the tools'
  gaps, fixed rather than worked around.
- **Did the plan hold?** Yes, after the two fixes.
- **One change that would have helped:** a tool that moves a static method to a
  class of another file (expand, migrate and contract by hand took three
  batches). Not added: this is its first use.

## Follow-ups

- 22 single-type visitors onto `Nodes.all` as their classes change.
