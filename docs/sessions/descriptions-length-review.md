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

## Notes

- A cloud session with no MCP server: every change to `Tools.java` went through
  `bin/jrefactor batch` as `replace_text` on the description's text block and on argument
  strings. `Arg.java` was read once with grep where `inspect` with `pattern` would have served.
