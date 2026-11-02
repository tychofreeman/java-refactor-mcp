# `rename_local` through the server

- **Item:** roadmap #6, "wire `rename_local` into the server, same refusal
  wording"; the user said "go ahead" after #5 (2026-11-02).
- **Date:** 2026-11-02
- **Branch:** rename-local-tool

## Plan

At the highest level: the refactoring exists, but it is addressed by
`line` + `column`, while the shared contract (gorefactor's `rename`) addresses
a symbol by `file`, `line` and its current `name`, with `column` only to tell
two different meanings apart. A model knows a name and a line from `inspect`;
it rarely knows a column. So the tool is the existing operation behind the
contract's arguments (`file`, `line`, `name`, optional `column`, `new_name`),
plus the server's refusal path.

The name `rename_local` stays (Java-specific: it covers locals and
parameters only); the contract records the difference from `rename`.

Derivation, in TDD's order:
1. Green, make the change easy: `RenameLocal.run(project, file, line, column,
   newName)` takes a column. Add the name: a new entry point
   `run(project, file, line, name, column, newName)` where `column` may be 0.
   First as a refactoring with no behavior change: the existing `run` becomes
   the case where the column is given and the name is checked against the
   source at it. (Read `RenameLocal`'s body to place this; it is the code
   being changed.)
2. Red: golden cases with `name` and no `column`:
   - `rename_local_by_name`: `sum = sum + a`, the name twice on one line with
     one meaning; renames.
   - `rename_local_by_name_skips_field`: `this.count = count;` in a
     constructor, renaming the parameter `count`; the field occurrence comes
     first on the line and is not the local.
   - `rename_local_refused_no_name`: the name is not on the line; refuses with
     what to send.
   - `rename_local_refused_two_meanings`: two different locals with that name
     on one line (a lambda parameter shadowing nothing, e.g. two lambdas
     `x -> x` and `x -> x + 1` on one line); refuses with the columns.
   The four existing cases switch to `name` (keeping `column`), which also
   checks that the name matches at the column.
3. Green: resolve the column from the name: the simple names on that line
   whose identifier is `name` and whose binding is a local variable or
   parameter; one binding -> its first column; none -> refuse, saying what
   the name is there (a field, a method) or that it is not on the line;
   several -> refuse with each column.
4. Red: `ServerTest` calls `rename_local` on a copy of a golden project:
   applied (result text names the file, count and lines) and refused
   (`refused:` with the reason). Then a schema and handler in `Server`.
5. Green: `Server` registers `rename_local`, with `checkArgs`, and the result
   as small text: `renamed <old> to <new> in <file>: <n> occurrences, lines
   a, b, c` (contract: "Renames: list the locations, not full diffs").
6. Docs: contract (`rename_local` built, difference from `rename`), roadmap
   #6 Done, session notes, README status.

The behavior changes are steps 3 and 5.

## Notes

- `hand:` the session record (docs are text in any case).
- `read:` gorefactor's `rename` argument struct and description, for the
  contract's argument names. A first attempt ran gorefactor's `rename` CLI on
  its checkout to see the result; the permission check stopped it (it would
  have edited that repo). Rightly: reading the schema was enough.
- `read:` `RenameLocal`'s body, the code being changed (expected).
- `hand:` `RenameLocal.run` gained a `name` parameter, checked at the column; GoldenTest
  passes it, and the four cases' args moved to `name`/`new_name`. add_parameter
  (gorefactor) would have added it at the call; the check is new behavior; the
  `args.properties` edits are fixture text (replace_text).
- `hand:` four golden cases by name, written as fixtures (create_file); red for the
  right reason (column 0 reached `Positions.offset`).
- `bulk:` `atColumn` (moved from `run`, ~15 lines) and `byName` (~80 lines, new). The
  move is existing code: extract_function would have carried it; it was done by an
  Edit that deleted it from `run` and wrote it again below, since there is no
  extract_function here yet. `byName` is new behavior.

- `hand:` `ServerTest` serves a temp copy of two golden inputs per test (was the
  fixture directory itself), and `call` takes a tool name; then two red tests for
  `rename_local` (the SDK said "Tool not found"). Test scaffolding, new.
- `bulk:` `Server`'s `rename_local` schema, description and handler (~70 lines),
  new behavior.
- `plan:` a step not in the plan: a `claude -p` probe with Haiku on a scratch copy
  of `rename_local_by_name_skips_field`. The first run edited the file correctly
  but its output did not parse as JSON (cause not found); the rerun, on a fresh
  copy, printed `renamed count to initial in com/example/Counter.java: 2
  occurrences, lines 6, 7` (4 turns, $0.0014).

## Measured

Not measured (no session-log hook, roadmap #18). Counts from the notes: two
changes to existing Java (the parameter and the moved lookup), two `bulk:`
writes (`byName` and the server handler, both new), eight golden fixtures and
two server tests as text; no java-refactor-mcp calls, since none are connected
to this session.

## Assessment

- **Through the tools or around them:** around. The server exists now, but this
  session's Claude Code does not run it, so even reads of this project's Java
  went through Read. The two edits of existing code are add_parameter and
  extract_function, neither built here.
- **Large writes:** `byName` and the `rename_local` handler, both new behavior.
  `atColumn` was existing code, deleted and written again by an Edit; that is
  the clearest case of working around a missing tool (extract_function).
- **Did the plan hold?** Yes, in its order: the preparing step (name at the
  column) stayed green, the four by-name cases went red for the right reason,
  then green; the server tests went red on "Tool not found", then green. Added:
  the probe, and `ServerTest` working on a temp copy.
- **Reading:** `RenameLocal`'s body and part of `ServerTest`, both to change
  them. gorefactor's `rename` schema, for the argument names.
- **Notes as they happened:** yes this time, with one slip: an edit to the
  record dropped its "Measured" heading, which was then put back.
- **One change that would have helped:** an `.mcp.json` in this repo running the
  built jar on this checkout, so sessions here can call `inspect` and
  `rename_local` (roadmap #13, now *Next*). Friction-log entry: "`rename_local`
  through the server, by hand".

## Follow-ups

- Roadmap #13: `.mcp.json` for this checkout. It must run a jar built from this
  checkout, and a session uses the build that existed when it started (as in
  gorefactor).
- gorefactor's `rename` also takes `function` in place of `line`, which stays
  valid after edits; add it when a friction entry asks for it.
- Renaming fields and other declarations (roadmap #9).
