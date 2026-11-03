# Friction log

Log each time a tool got in the way, a refusal was unclear, or a refactoring had
to be done by hand. The roadmap is ordered by this log, as it is in gorefactor.

Format, one entry per event. Append new entries at the end of the file (oldest
first), so parallel PRs do not conflict at the same place:

```
## YYYY-MM-DD: short title
- Task: what the session was doing
- Tool: which tool (or none, if it was by hand)
- What happened: what got in the way, with the refusal or error text if any
- Cost: rough extra calls or tokens, if known
- Idea: what would have helped, if obvious
```

Before writing that no tool covers a change, look for a sequence of two or three
tools that composes to it, and name the sequences tried and why each failed.
When a later PR closes an entry, mark it *Follow-up: done* with the PR.

## 2026-11-02: the first server and `inspect`, all by hand
- Task: roadmap #5, the MCP server over STDIO with `inspect`
  (`docs/sessions/mcp-server-inspect.md`).
- Tool: none; the server did not exist until the end of the PR.
- What happened: every Java change was a hand edit. The ones a tool would have
  done: extracting `GoldenTest.call` from `runCase` (extract_function), the
  `Inspect` and `Server` stubs from the tests' calls (create_from_usage), and
  replacing the stubs' bodies (replace_declaration). The rest was new behavior.
- Cost: not measured (no session-log hook yet, roadmap #18).
- Idea: none new. The gap is the whole edit tool set, which the roadmap already
  orders: `rename_local` through the server (#6), then building this project
  through its own tools (#13).

## 2026-11-02: `rename_local` through the server, by hand
- Task: roadmap #6 (`docs/sessions/rename-local-tool.md`).
- Tool: none; this session's Claude Code has no java-refactor-mcp connected
  (no `.mcp.json` yet).
- What happened: two changes to existing Java that tools would have done:
  adding `name` to `RenameLocal.run` and its caller (add_parameter), and moving
  the column lookup out of `run` into `atColumn` (extract_function). The rest
  was new behavior. `inspect` existed, but was not callable from this session.
- Cost: not measured (roadmap #18).
- Idea: `.mcp.json` running this checkout's jar (roadmap #13), so the next item
  can read with `inspect` and rename with `rename_local`. Then add_parameter and
  extract_function are the edit tools this item would have used.

## 2026-11-02: `.mcp.json` for this checkout, by hand
- Task: roadmap #13 (`docs/sessions/mcp-json-self.md`).
- Tool: none in this session (it started before `.mcp.json` existed).
- What happened: the change to existing Java was `Server.main` (the exit at the
  end of input) and `answer` (counting calls); both new behavior, written by
  Edit. The launcher, `.mcp.json` and docs are not Java.
- Cost: not measured (roadmap #18).
- Idea: none; from the next session on, `inspect` and `rename_local` are there.

## 2026-11-02: source roots and the classpath, reads through `inspect`
- Task: roadmap #7 (`docs/sessions/source-roots-classpath.md`).
- Tool: `inspect` for every read of this repo's Java; no edit tools exist.
- What happened: edits to existing code by hand: a `sourceRoots` component on
  `SourceProject` and its uses (add_field, replace_expression), `Jdt.parse`'s
  environment (replace_statements), `Server.main` calling `load`
  (replace_expression). New code (`MavenBuild`, `load`, the test) by Write.
  Also: `rename_local`'s description, as a model sees it, has runs of spaces
  ("only       when"), because a text block's `\` continuation keeps the next
  line's indentation.
- Cost: not measured (roadmap #18).
- Idea: roadmap #19's edits cover each of these. The description needs its
  continuation lines unindented, and a test that no description has a run of
  spaces.

## 2026-11-02: the first edit tools, built by hand
- Task: roadmap #19 (`docs/sessions/first-edit-tools.md`).
- Tool: `inspect` for reads of this repo's Java; the edit tools did not exist in
  this session (it uses the build from when it started).
- What happened: moves of existing code by hand: `Gate.apply` out of
  `RenameLocal.run` (extract_function), `Server`'s tool constants, handlers and
  argument helpers into `Tools` and `Args` (move_declaration), about 150 lines
  written out again. Correction to the entry on source roots: the runs of spaces
  in `rename_local`'s description came from writing the Java through a Python
  string, not from text block continuations.
- Cost: not measured (roadmap #18).
- Idea: extract_function (#23); a move of declarations between files is not on
  the roadmap; log it again when it recurs before adding it.

## 2026-11-02: imports, the first item built through the edit tools
- Task: roadmap #20 (`docs/sessions/imports-in-edits.md`).
- Tool: `inspect`, `create_file`, `replace_text`, `add_declaration`,
  `replace_declaration` for every change to Java; golden fixtures by shell.
- What happened:
  - Four refusals for a missing import (`Arrays`, `List`, `ONE_AT_A_TIME`
    declared after its use, and the signature change below), each answered with
    another tool call first: the gap this item closes, except the last.
  - Changing `Edits.write`'s signature and its caller: each alone was refused
    (the other did not compile). Composed as add the new overload
    (add_declaration), move the caller (replace_text), delete the old one
    (replace_declaration with empty content). Three calls where batch with check
    "end" would be one (roadmap #8).
  - `add_declaration`/`replace_declaration` refuse unparseable content without
    saying where; `create_file` names the lines. Cost one blind retry.
  - One `replace_text` `old` did not match text that `inspect` showed; a shorter
    `old` from the same lines matched. Cause not found.
- Cost: not measured (roadmap #18).
- Idea: batch (#8, next); line numbers in the members-do-not-parse refusal.

## 2026-11-02: undo and batch, through the tools
- Task: roadmap #8 (`docs/sessions/undo-and-batch.md`).
- Tool: every change to Java through java-refactor-mcp; fixtures by shell.
- What happened:
  - Imports missed: `ArrayDeque`, used only in `computeIfAbsent(k, x -> new
    ArrayDeque<>())`, was not imported by the edit, so the gate refused it.
    Answered by adding the import with `replace_text` first. A bug in #20's
    unresolved-name detection; follow-up.
  - Ambiguous names (`Path`, `Pattern`) refused correctly, but the example in
    the refusal (`as imports: [...]`) names the last candidate alphabetically
    (`org.eclipse...`), not the likely one (`java.*`).
  - A `replace_text` `old` copied from `inspect` with two spaces too many of
    indentation: `inspect` prints the number, two spaces, then the code. The
    cause of the unexplained mismatch in #20 too. gorefactor tolerates spacing
    differences and names the nearest lines in the refusal.
  - Twice, a use and its declaration sent in one turn with the use first: the
    use was refused, the declaration applied. Calls run in the order sent.
  - The probe found `replace_declaration` over-indenting content whose first
    line was at column 0 and the rest as in the file; fixed here with a case.
- Cost: not measured (roadmap #18).
- Idea: the nearest lines in "old is not in", and spacing-tolerant matching;
  the likely candidate first in an ambiguity refusal; the lambda import bug.

## 2026-11-02: imports in lambdas, through the tools
- Task: the import fixing missed `ArrayDeque` in a lambda (#8's follow-up).
- Tool: every change through java-refactor-mcp; no hand edits.
- What happened:
  - The first two fixtures (a lambda alone, then a chained call) imported
    `ArrayDeque` fine. The miss needs the call's receiver unresolved too:
    JDT does not type-check a lambda whose target type it cannot find, so
    `ArrayDeque` is reported only after `Map` and `Deque` are imported. Fixed
    by parsing again with the imports found, until none are added.
  - `add_declaration` into `public class A {}` in a file with no indented line
    indents members by 4 (the default step) while the content's own step is 2.
    Worked around with an indented field in the fixture.
- Cost: not measured (roadmap #18).
- Idea: with no indentation in the file, take the step from the content.

## 2026-11-02: stubs from usage, through the tools
- Task: roadmap #21 (`docs/sessions/stubs-from-usage.md`).
- Tool: every change through java-refactor-mcp; no hand edits.
- What happened:
  - `imports` sent at the top of a `batch` instead of on the step: refused
    ("batch does not take imports"), which was right.
  - A 25 KB `replace_text` resent twice: first because `spotless:apply` had
    reformatted the text I copied as `old` from my own earlier write, then
    because `ConditionalExpression` was ambiguous with
    `org.eclipse.jdt.internal.compiler.ast`. Most `org.eclipse.jdt.core.dom`
    names are; I passed 44 names in `imports`. A refusal that listed every
    ambiguous name at once would have saved one resend; discouraging
    `.internal.` packages would have saved both.
  - The indentation default of 4 in a file with nothing indented (logged with
    the lambda fix) is fixed here: the step comes from the content.
  - Probe: once Haiku undid the stubs, reading "do not implement them" as "do
    not add them"; once it could not find the files (there is no listing tool,
    and it guessed Maven paths); with paths given, one `add_declaration`.
- Cost: not measured (roadmap #18).
- Idea: discourage `.internal.` packages in the import index; name every
  ambiguous name in one refusal; a way to list the project's Java files.

## 2026-11-02: stubs for a class used as a static receiver
- Task: registering #22's tools with handlers not written yet
  (`Signatures.addParameter(...)` in a lambda), to let stubs declare them.
- Tool: add_declaration, refused.
- What happened:
  - `Signatures cannot be resolved` is UndefinedName, not UndefinedType, so no
    class was declared.
  - The import fixing imported `jdk.javadoc.internal...Signatures`, from a
    package the JDK does not export ("The import jdk.javadoc.internal cannot
    be resolved").
  - The call is a lambda's body, whose result type stubs did not infer.
  Fixed in their own PR before #22: a static receiver nothing declares becomes
  a class, a lambda's result type comes from the method it implements, and
  libraries' `.internal.` packages are not import candidates (this also ends
  the dom/internal ambiguity logged above).

## 2026-11-02: signature changes, through the tools
- Task: roadmap #22 (`docs/sessions/signature-changes.md`).
- Tool: every change through java-refactor-mcp; no hand edits.
- What happened:
  - A batch checked at each step: step 1 called `allPure(...)`, which step 2
    declared. Step 1's gate declared a stub `allPure(List)` (raw, from
    `extendedOperands()`), and step 2's `allPure(List<?>)` then clashed with it
    ("Erasure of method allPure(List) is the same"), since a stub is replaced
    only when the parameter types are written the same. Resent with check
    "end". Two fixes would each have avoided it: replace a stub whose
    parameters have the same erasure; or, in a batch, declare stubs only after
    the last step that could declare the name.
  - `GoldenTest` reads `value=10` in `args.properties` as a number, which the
    tool refuses ("value must be a string"); moved to `value.txt`.
  - `./mvnw -q test | grep` hid a compile failure (a -Werror warning, a raw
    `stream()`): the quiet grep showed nothing, as for a pass. Check the build
    result, not the absence of failures.
  - Probe: Haiku read "an int priority parameter, last" as a parameter named
    `last`; with "named priority, at the end", one `add_parameter` call.
- Cost: not measured (roadmap #18).
- Idea: stub replacement by erasure.

## 2026-11-03: extract method, through the tools
- Task: roadmap #23 (`docs/sessions/extract-function.md`).
- Tool: every Java change through java-refactor-mcp; one hand edit to
  `docs/design.md` (a roadmap row the user asked for mid-item).
- What happened: `create_file` of the 1000-line `Extract` was refused for two
  compile errors (a name clash and a private helper it called), and the whole
  file had to be sent again. A refused `create_file` of a large new file costs
  its size per retry.
- Cost: not measured (roadmap #18).
- Idea: let `create_file` of a new file that fails only the type check be
  fixed by edits before the gate runs: in a batch with check "end", create the
  file and then `replace_text` it, which works today; say so in the refusal
  for a large file ("send it in a batch with check end, with the fixes as later
  steps").

## 2026-11-03: a refused create_file left a stub class behind
- Task: roadmap #18 (session log), writing its test first.
- Tool: `create_file` of a test that used a class nothing declared, and a
  constant of it (stubs do not declare fields), was refused, but the stub
  class it had declared stayed on disk; the next `create_file` of that class
  was refused as "already exists".
- Cause: the before-text of a new file is null, and `Map.putIfAbsent`
  replaces a null, so the second stub round recorded the stub as the file's
  old text and the refusal restored it. `Journal.wrote` had the same bug:
  undoing a batch that created a file and then edited it would have left the
  file. Fixed with `containsKey` in both (`stubs_refused_leaves_no_class`,
  `JournalTest`).
- Idea: none beyond the fix.

## 2026-11-03: stubs do not declare a constant a test uses first
- Task: roadmap #18 (`docs/sessions/session-log.md`).
- Tool: `create_file` of `SessionLogTest`, which used `SessionLog.MAX_RESULT`;
  refused ("MAX_RESULT cannot be resolved or is not a field").
- What happened: resent in a batch with `create_file` of a `SessionLog` that
  held the constant first. One extra call, and the test resent whole.
- Idea: a stub for a static field read as `T.NAME` (typed from the use, as
  methods are, with a default value) when the type is the project's.

## 2026-11-03: an import for a name a later batch step declares
- Task: roadmap #9 (`docs/sessions/rename-across-files.md`).
- Tool: `batch` with check "end": step 1 replaced `RenameLocal.run` with a
  body that used `Query`, a nested record step 4 added. Step 1's import fix
  found one class of that name, `javax.management.Query`, and imported it.
  It compiled (the member type shadows the import) and stayed until removed
  by hand with `replace_text`.
- Idea: in a batch checked at the end, fix imports once at the end too, or
  have a later step's import fix drop an import that a member type of the
  file now shadows.

## 2026-11-03: one refused step costs the whole batch again
- Task: roadmap #9.
- Tool: a five-step batch of about 15 KB (the field rename) was refused twice:
  step 1 deleted a record `run` still used (the import fixer then had two
  `Result` classes), and step 4 needed `imports` for `QualifiedName`. Each time
  the whole batch was sent again to change one step.
- Idea: an ambiguous import in a batch checked at the end could take the
  candidate in the same package family as the file's other imports
  (`org.eclipse.jdt.core.dom`, like the file's 14 others) rather than refuse;
  and a deleted declaration still used could be deferred to the end check.

## 2026-11-03: stubs declared a local assertEquals for a missing static import
- Task: roadmap #9, a `JournalTest` method using `assertEquals` when the
  file imported only `assertFalse`.
- Tool: `add_declaration` declared a stub `JournalTest.assertEquals(String,
  String)` instead of importing `Assertions.assertEquals` statically. A
  second batch deleted the stub and added the import with `replace_text`.
- Idea: before declaring a stub method on the enclosing type, look for a
  static import of the same class as an existing static import
  (`org.junit.jupiter.api.Assertions.*`) that has the method.

## 2026-11-03: no tool moves golden directories
- Task: roadmap #9: eight `rename_local_*` golden cases renamed to `rename_*`,
  and two copied into new cases.
- Tool: by hand, `git mv` and `cp -r`. Compositions tried: `create_file` of
  each file under the new name and then deleting the old (no tool deletes a
  file that is not Java). gorefactor has a file move with copy for this
  ("testdata/old_case to testdata/new_case renames a golden case").
- Idea: a `move_file` (with `copy`) for fixtures and docs, as gorefactor's.

## 2026-11-03: session report counts reads as hand writes
- Task: roadmap #9, `bin/session-report` for the record.
- What happened: `grep` commands with `<` or `>` in the pattern
  (`List<Path>`) were listed under "by hand", and a Python heredoc that
  edited `docs/tool-contract.md` was counted as "44 lines of Java". The
  rename sent to the branch's jar over stdin is not counted as a step.
- Idea: count a Bash command as a write only when a redirect target or a
  writing command names a file, not for `>` inside quotes; count lines of
  Java only for `.java` targets.
