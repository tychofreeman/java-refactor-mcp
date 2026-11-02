# The first edit tools

- **Item:** roadmap #19, position 2 of the eight the user asked for (2026-11-02):
  `create_file`, `replace_text`, `add_declaration`, `replace_declaration`.
- **Date:** 2026-11-02
- **Branch:** first-edit-tools

## Plan

At the highest level: so far the server can read and rename, and every other
change to Java is written by hand. gorefactor's share of edits made through its
tools went from 10-20% to 80-99% on the day these four came. They are edits:
behavior changes on purpose, held to the same rule as refactorings (the project
type-checks no worse after than before, or nothing changes).

Arguments follow gorefactor's, in Java's terms:
- `create_file`: `file`, `content`, `overwrite`. A `.java` file must parse and
  declare the package its directory under a source root implies. Other files
  are written as they are.
- `replace_text`: `file`, `old`, `new`, `all_occurrences`. `old` must occur once
  unless `all_occurrences`.
- `add_declaration`: `file`, `content` (member declarations: methods, fields,
  constructors, nested types), `type` (the type to add to; default the file's
  only top-level type), `after` / `before` (a member name). A method whose body
  is only `throw new UnsupportedOperationException("not implemented")` and that
  has the same name and parameter types is replaced, not duplicated.
- `replace_declaration`: `file`, `name` (`Type`, `Type.member` or `member` of
  the file's only top-level type; `method(int, String)` for one overload),
  `content`. The old Javadoc stays unless `content` has its own. Empty content
  deletes the declaration and its Javadoc.
- Results say what changed and where (file and lines), and never echo content.
- Imports are not added or removed yet (roadmap #20): `content` that names a new
  type needs an import added by `replace_text` until then.

Derivation, in TDD's order:
1. Green, make the change easy:
   - the gate is inline in `RenameLocal.run`. Extract it as `Gate.apply(project,
     changes)`: write the new texts, type-check, restore and refuse on new
     errors. `RenameLocal` calls it. Green.
   - `Server` registers each tool with the same lines of code. Turn that into a
     list of tool definitions, so a new tool is one entry. Green.
   - the description bug from #7: a red test first (no description has a run of
     spaces), then unindent the text block continuations. This is a fix, not a
     refactoring, so it goes red then green before the edit tools.
2. For each tool, in the order above: golden cases (applied, refused), red, the
   code, green; then a `ServerTest` call. Golden cases go through the same
   `call` dispatch as now.
3. Docs: contract sections; roadmap #19 Done and #20 Next; CLAUDE.md's "Using
   java-refactor-mcp" names the four tools; session notes.

## Notes

- `read:` gorefactor's four tools' descriptions and argument structs, for the
  contract's names.
- `hand:` `Gate.apply` extracted from `RenameLocal.run` (extract_function; none
  here). Green.
- `plan:` the runs of spaces in `rename_local`'s description were not the
  formatter: I wrote that text block through a Python script, and in a Python
  string `\` plus newline is a line continuation too, so Python joined the lines
  before Java saw them. Red test over every description, then the fix. Lesson
  for the notes: Java written through Python needs raw strings, or the Edit tool.
- `bulk:` `Args` and `Tools`, made from `Server`'s constants, handlers and
  argument helpers (about 150 lines moved, little changed). move_declaration
  would have carried them; there is none, so they were written out again. The
  only wording change: a missing `file` for `inspect` now says "inspect needs
  file." Green.
- `hand:` `GoldenTest.call` dispatches through `Tools.ALL` (properties and
  `<arg>.txt` files as arguments), and compares every file, not only `.java`.
  Green.
- `hand:` six `create_file` cases and the stub; red (6). `bulk:` `Edits.createFile`,
  `Edits.parses`, `Gate.applyOrRefuse`, `SourceProject.file`/`packageOf`,
  `Jdt.parseSyntax`: new behavior. Two fixtures had a trailing newline the result
  does not have; fixed. Green (18 golden).

- `hand:` six `replace_text` cases and the stub; red (6). `bulk:`
  `Edits.replaceText` with `existing`, `write`, `lineOf` (~90 lines): new. Green
  (24 golden).
- `hand:` 16 cases for `add_declaration` and `replace_declaration`, two stub
  definitions in `Tools`; red (16). `bulk:` `Declarations` (~450 lines with
  formatting), new behavior, written in one call; all 16 went green on the first
  run. Then a refactor while green: `add` built its result through a regex
  fix-up and guessed which change was a stub; each change now carries its own
  wording. A first try at that refactor by a Python script failed on an assert
  (Spotless had reflowed the lines it matched) and changed nothing; redone with
  Edit. Green.
- `hand:` a test that every tool in `Tools.ALL` is listed. Green (40 golden, 9
  server, 3 project).
- `plan:` a probe not in the plan: a fresh Haiku, allowed only the jrefactor
  tools, on a scratch copy: add `sum()`, change `b()`, create a doc. It used
  `add_declaration`, `replace_declaration` and `create_file` correctly (6 turns,
  $0.004, no refusals). It showed "(1 lines)"; fixed to "1 line". Its output
  had a warning line before the JSON because `claude -p` waits 3 s for stdin;
  that was also the unexplained "not JSON" in #6. Recorded in the session notes.

## Measured

Not measured (roadmap #18). From the notes: reads of this repo's Java through
`inspect` (5 calls) and Read only for files about to be edited by Edit
(`Server`, `Declarations`). No java-refactor-mcp edit calls on this repo: the
session's server predates the edit tools. Large writes: `Args` + `Tools`
(existing code moved), `Edits`, `Declarations` (new).

## Assessment

- **Through the tools or around them:** around, necessarily, for the edits:
  they did not exist in this session's server. The clearest work-around is the
  150 lines moved from `Server` into `Tools`/`Args` by writing them out again.
- **Large writes:** `Declarations` and `Edits` are new behavior. `Tools`/`Args`
  are existing code moved; no tool here moves declarations between files.
- **Did the plan hold?** Yes: the preparing steps (gate, tool list, description
  fix) came first and stayed green, then each tool red then green in the order
  planned. Two additions: dispatching golden cases through the tool
  definitions, and the probe.
- **Reading:** outlines and the ranges to change; `Server` and `Declarations` by
  Read before Edit.
- **What went badly:** two Python-script edits failed (an unescaped
  continuation earlier, an assert on reflowed text here) and one doc edit
  failed on quoting. Edit and Write did not fail.
- **One change that would have helped:** the edit tools in this session, which
  needs a restart after this PR merges. After that, the next items can be built
  through them.

## Follow-ups

- Imports (roadmap #20, now *Next*): `add_declaration`, `replace_declaration`
  and `create_file` should add the imports content needs and drop ones left
  unused.
- `replace_text` options gorefactor has: `files`, `function`, `append`.
- `replace_declaration` by `line`; deleting one of several fields declared in
  one statement.
- A move of declarations between files, if the friction log asks for it again.
