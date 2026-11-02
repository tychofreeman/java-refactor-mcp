# Imports added and removed by every edit

- **Item:** roadmap #20, position 3 of the eight the user asked for (2026-11-02).
- **Date:** 2026-11-02
- **Branch:** imports-in-edits

## Plan

At the highest level: an edit's content names types, and whether a name needs an
import is the server's to work out, not the model's. gorefactor fixes imports
after every edit (52 friction entries mention imports; "2026-10-20: imports
fixed after every edit"). So imports are not a tool: every edit of a `.java`
file (`replace_text`, `add_declaration`, `replace_declaration`) ends by
- adding an import for each simple type name the edit left unresolved, when
  exactly one class has that name (this project's sources, the classpath, the
  JDK; `java.awt`, `javax.swing`, `sun.*`, `com.sun.*`, `jdk.*` only when
  nothing else has the name), in order among the file's imports;
- removing single-type imports the edit left unused (only those used before it:
  an import that was already unused stays);
- refusing when a name has several candidates, listing them, unless `imports`
  (fully qualified names) chooses.
`create_file` content is a whole file with its own imports, so it is left alone.
Results add "; imported a.B" and "; removed import c.D", and line numbers
account for the lines imports add or remove above the change.

Derivation:
1. Green, make the change easy: `Declarations.add` formats its result while it
   builds the text, so lines cannot be shifted later. Keep ranges as numbers and
   format after (replace_declaration of `add`). Green.
2. Red: golden cases: imports added to a file with none and to one with some (in
   order), a project type imported, an import removed by replace_declaration, a
   pre-existing unused import kept, an ambiguous name refused, `imports`
   choosing. Stub `Imports.fix` returning the text unchanged.
3. Green: `Imports` (new file, create_file): a class-name index built once per
   server (JDK through `jrt:/`, classpath jars, source files), `fix(project,
   before, after, chosen)`. Callers: `Edits.write` and `Declarations`
   (replace_declaration and add_declaration on them).
4. Docs: contract (imports, the `imports` argument), descriptions of the three
   tools (the "Imports are not added yet" lines go), CLAUDE.md, roadmap #20 Done.

## Notes

- `plan:` this is the first item built in a session that has the edit tools, so
  Java goes through them; fixtures may be written by shell, and each such batch
  is noted.
- `read:` the server's description of `rename_local` as this session's client
  listed it still had runs of spaces, though the jar sends it without them
  (checked with a piped `tools/list`). So the listing comes from the client's
  side, not the server; cause not found.

- `refusal:` none for step 1: `replace_text` on `Declarations.add`'s last lines
  (record block offsets, format after). Green (40 golden).
- `hand:` eight golden fixtures written by shell (test data, not code).
- `refusal:` `replace_text` in `GoldenTest` refused: `Arrays cannot be resolved`.
  Answered with `replace_text` adding the import first, then the same edit.
  The gap this item closes.
- `refusal:` `add_declaration` of `Args.list`: `List cannot be resolved`. Same
  answer.
- `refusal:` `replace_declaration` of `Edits.write` with a new signature, and
  `replace_text` of its caller, sent together: each refused because the other
  had not happened. Composed: `add_declaration` of the new overload, then
  `replace_text` of the caller, then `replace_declaration` deleting the old
  overload by its parameter types. Batch with check "end" (#8) is the one-call
  form.
- `refusal:` a `replace_text` whose `old` I copied from `inspect` was "not in"
  the file; a shorter `old` from the same lines matched. I first put it down to
  `\n` in the argument arriving as a newline; a later call showed arguments
  arrive literally, so the cause is not found.
- `refusal:` the first `replace_declaration` of `Imports.fix` (300 lines):
  "content does not parse", no line given. I had written `'\\n'` for the reason
  above, which is not valid Java. Resent with `\n`; applied. The refusal should
  name the line, as `create_file`'s does.
- `bulk:` `Imports.fix` and its helpers (about 300 lines with formatting), new
  behavior, in one `replace_declaration` of the stub. Green on the first run
  (48 golden).
- `refusal:` none for the refactors: `replace_declaration` deleting the unused
  `range` overload, `replace_text` of its stale `{@link}` (the gate does not see
  Javadoc links) and of three tool descriptions.
- `plan:` timing two piped edits showed the second undoing the first: the SDK
  ran both calls at once, each read the file before the other wrote. Not this
  item's bug (it was there since #19), but an edit tool that loses edits blocks
  everything after it. A red test (3 of 3 runs), then a lock around running a
  tool. My own two calls for it went in the wrong order in one turn (use before
  declaration): the first refused, the second applied; resent.
- `plan:` a server start and two import-fixing edits, piped: 2.2 s.

## Measured

Not measured (roadmap #18). Counted from the notes: 24 java-refactor-mcp calls
for this item's Java (inspect 4, replace_text 13, add_declaration 4,
replace_declaration 4, create_file 2), 7 of them refused and answered with
another call. No Java written by Edit, Write or a shell. Fixtures (eight cases)
and docs by shell.

## Assessment

- **Through the tools or around them:** through them, for every line of Java,
  for the first time in this project. Each refusal was answered with another
  tool call, not a hand edit.
- **Large writes:** `Imports.fix` with its helpers, new behavior; nothing existing
  was written out again.
- **Did the plan hold?** Yes, in order. Added: the composition for a signature
  change, and the concurrency fix, which the timing found.
- **Reading:** `inspect` ranges of the code to change; no whole-file reads.
- **What went badly:** seven refusals, four of them for imports (the gap being
  built), one for a dependent pair (batch), one for my wrong escaping, one
  unexplained `old` mismatch. The members-do-not-parse refusal gave no line,
  which cost a blind retry.
- **One change that would have helped:** batch with check "end" (roadmap #8,
  now *Next*): the signature change was three calls and a refusal.

## Follow-ups

- Line numbers in the refusal for content that does not parse as members.
- The unexplained `old` mismatch in `replace_text`: if it recurs, show the
  nearest lines in the refusal, as gorefactor's does.
- Static imports and on-demand imports are left alone; add when needed.
- The client's listing of `rename_local`'s description (runs of spaces, while
  the jar sends none).
