# Tool descriptions: no refusal lists, no shared boilerplate

- **Item:** the user, 2026-11-06, asking why the descriptions are so long, then: change
  CLAUDE.md not to require refusals in the description (the refusal explains itself), and do not
  make the descriptions repeat themselves, especially about details that should not concern the
  model ("must type-check no worse").
- **Date:** 2026-11-06
- **Branch:** descriptions-without-refusals

## Plan

1. CLAUDE.md: a description says what the tool does, not what it refuses; the refusal explains
   itself; `docs/tool-contract.md` keeps the lists. Shared behavior is said once.
2. The 28 "Refuses, saying why: ..." paragraphs out of the descriptions.
3. The repeated "type-checks the project ... rolled back and refused", "must type-check no worse",
   "stubs and imports as for every edit" and ": an edit" out; the stub and import rules said once
   in the server's MCP `instructions` (`Server.INSTRUCTIONS`).
4. `preserve_exceptions`' description (in about 25 schemas) cut to one line; `add_declaration`
   rewritten short.

## Notes

- The edits to `Tools.java` went through `bin/jrefactor` as two batches of `replace_text` steps
  (27 and 24), built by a script from `inspect`'s numbered lines so each `old` is exact (the
  second by regular expressions over the reconstructed text, previewed before it ran); a line
  `inspect` split ("Refuses, saying" / "why:") was missed by the first and caught by the second.
- read: the descriptions (to cut them), `Server.start` (where to add the instructions), and
  `javap` of the SDK for `instructions(String)`.
- The new `replace_text` matched an `old` two spaces too deep in `Server` and moved `new` with it.

## Measured

The published descriptions and schemas (`bin/jrefactor list` of each tool): about 58,000
characters before, 44,500 after (23% less); `add_declaration` 3,033 to 1,713, `create_file` 1,761
to about 1,200. Through jrefactor: two CLI batches (51 steps), four edit calls; by hand this
record.

## Assessment

Through the tools. The descriptions now differ from what the A/B baselines saw, so an A/B check
of the shorter list is due (ask first; about $3 for warehouse and shop's mcp arms).

## Follow-ups

- The A/B check.
- A per-tool size limit as a test, as gorefactor's `TestLoadedToolsStaySmall` (1,700 characters),
  if the user wants one; `add_declaration`, `extract_struct`, `fold`, `move` are still above it.
