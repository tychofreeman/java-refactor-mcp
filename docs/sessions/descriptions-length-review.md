# A review of the tool descriptions for length and wording

- **Item:** the user asked (2026-11-13) for a review of the tool descriptions "to make sure they
  remain short, and are well written", then for issues and for each to be worked in turn.
- **Date:** 2026-11-13
- **Issues:** #417 (`add_parameter`), #418 (`inline`, `pull_up`, `push_down`), #419
  (`convert_loop`, `move_statement`, `fold` and smaller repeats), #420 (a size test). One branch
  and PR each; this record is extended by each.

## What the review found

Measured from `Tools.java` at f32f78b: 40 published tools, about 27,800 characters of
description, 35 of them under 1,000. Nothing bounds a description's size; gorefactor's
`TestLoadedToolsStaySmall` holds a definition to 1,700.

| Tool | Description | With its arguments' text |
|---|---|---|
| `add_parameter` | 2,334 | about 3,000 |
| `fold` | 1,485 | about 1,700 |
| `type_switch_to_method` | 1,425 | about 1,600 |
| `insert_statements` | 1,215 | about 1,300 |
| `remove_parameter` | 1,167 | about 1,600 |

Wording: `inline` says "static method" though instance methods are inlined (roadmap #84);
`pull_up` and `push_down` name Sub and Base without introducing them; `convert_loop` is one
paragraph in which `to=while` and `to=for` each appear twice; `move_statement` and `fold` list
what they refuse or leave, against the rule of 2026-11-06; `extract_variable` explains `as`
twice; `create_file` always describes skeleton-only mode.

The descriptions to copy: `move`, `extract_type`, `inspect` and `rename` (one sentence, one
before/after, the cases as a list).

## #417: `add_parameter`

2,334 characters to 1,420. The method case and its example are as they were. The record, the
enum and the type parameter are a list under "name may also be", one example each. Dropped from
the description, each still in `docs/tool-contract.md` and in the refusal or result that
applies: a use in the class's own instance code becoming `Box<T>`; that calls of a generic
method still infer the value; that an enum's constructor is written when it has none; the
record's "implicit or compact" condition; `Color(String)` for the constructor alone (the refusal
names it). Moved to argument text: `before` also places a type parameter; `members`' bodies are
not changed.

It is still the longest description: five targets share one tool by the user's choices of
2026-11-13 (generics slice 1, enums slice 2), and a model has to see each to know the tool fits.

No A/B run: the change is to what a fresh model reads, so the baselines shift with it.

## #418: `inline`, `pull_up`, `push_down`

- `inline` said "every call of the static method". Instance methods are inlined too (roadmap
  #84; the golden `inline_function_instance`), so it says "the method", the call's receiver
  taking the place of `this`; the `name` argument no longer says "static". #368 rewrites this
  description again when one call can be inlined.
- `pull_up` and `push_down` named Sub and Base without saying what they were, and in
  `push_down` Sub was the class the member leaves. Each has one example now (Circle, Square and
  their superclass Shape), and `name` is described as `Class.member`, since a field may be
  named. Where the member lands is no longer said: placement is the tool's (2026-11-07).

## #419: `convert_loop`, `move_statement`, `fold`, `extract_variable`, `create_file`

- `convert_loop` (910 to 886): a list by `to` value.
- `move_statement` (934 to 635): the paragraph of what never moves is gone.
- `fold` (1,485 to 1,167): the list of why a match is left, and the `String.format` rule.
- `extract_variable` (843 to 668): `as` once; its argument keeps the rule.
- `create_file` (915 to 748): the skeleton-only sentence is gone. The issue asked to show it
  only under skeleton-only; that needs descriptions built from session state, and the refusal
  in `Edits.createFile` already says what to send, so it was dropped instead (said in the PR).

Everything dropped is in `docs/tool-contract.md` and said by the refusal or result that applies.

## #420: the size test

`ServerTest.definitionsStaySmall` measures each tool as `tools/list` sends it (its JSON:
description and input schema), as gorefactor's `TestLoadedToolsStaySmall` does. After the three
trims, 15 of 40 were over gorefactor's 1,700, since the schema adds 300 to 1,000 characters.
The user chose (2026-11-13) a ratchet: 1,700 for every tool, and the 15 listed in
`DEFINITION_OVER` at their size then, so each may shrink and not grow. Over: one limit at 2,600
(a 500-character tool could have grown fivefold), and trimming all 15 first.

| Over 1,700 | Characters |
|---|---|
| add_parameter | 3,374 |
| change_signature | 2,596 |
| insert_statements | 2,518 |
| remove_parameter | 2,349 |
| change_type | 2,257 |
| type_switch_to_method | 2,154 |
| replace_statements | 2,114 |
| extract_type | 2,048 |
| extract_variable | 2,029 |
| move | 1,976 |
| fold | 1,897 |
| extract_function | 1,852 |
| batch | 1,801 |
| add_declaration | 1,776 |
| inline | 1,760 |

## Notes

- The three trims were opened as independent PRs off `main` (#421, #425, #427), since merging
  from this session was refused; this one contains their commits until they are merged.
- A cloud session with no MCP server: every change to `Tools.java` went through
  `bin/jrefactor batch` as `replace_text` on the description's text block and on argument
  strings. `Arg.java` was read once with grep where `inspect` with `pattern` would have served.
