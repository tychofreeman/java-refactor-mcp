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
In markers, `#N` is a pull request and `roadmap #N` a row of the roadmap in
`docs/design.md`; older entries write rows as `#N` too.

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
- Follow-up: done: the edit tools (roadmap #19), `.mcp.json` (roadmap #13),
  `extract_method` (#21) and stubs (#18).

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
- Follow-up: done: `.mcp.json` (roadmap #13), `add_parameter` and
  `extract_method` exist.

## 2026-11-02: `.mcp.json` for this checkout, by hand
- Task: roadmap #13 (`docs/sessions/mcp-json-self.md`).
- Tool: none in this session (it started before `.mcp.json` existed).
- What happened: the change to existing Java was `Server.main` (the exit at the
  end of input) and `answer` (counting calls); both new behavior, written by
  Edit. The launcher, `.mcp.json` and docs are not Java.
- Cost: not measured (roadmap #18).
- Idea: none; from the next session on, `inspect` and `rename_local` are there.
- Follow-up: no gap to fix: new behavior, before the tools could be used.

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
- Follow-up: done: the edits (roadmap #19); `ServerTest.descriptionsHaveNoRunsOfSpaces`.

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
- Follow-up: done: `extract_method` (#21); moving declarations between files is
  `move_type` (#47) and, within a file, `move_member` (#114); across files, open
  (roadmap #109).

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
- Follow-up: batch is done (roadmap #8); the members-do-not-parse refusal
  names its line since roadmap #110 (2026-11-06); the unmatched `old` was indentation (next
  entry).

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
- Follow-up: the lambda import is done (#17); the nearest lines and
  indentation-tolerant `old` are done (roadmap #106, 2026-11-06); the likely candidate
  first is done (roadmap #105, 2026-11-05).

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
- Follow-up: done: the reparse (#17) and the indentation step from the content
  (#18).

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
- Follow-up: `.internal.` packages are left out (#19); every ambiguous name in one
  refusal is done (roadmap #105, 2026-11-05); listing the project's files is done (roadmap
  #111, 2026-11-06: `inspect` with no arguments).

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
- Follow-up: done (#19).

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
- Follow-up: done: a stub is replaced by erasure (roadmap #107, 2026-11-06).

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
- Follow-up: done (roadmap #110, 2026-11-06): a `create_file` compile refusal suggests a
  batch with check "end".

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
- Follow-up: done in that item's PR (#23; golden `stubs_refused_leaves_no_class`).

## 2026-11-03: stubs do not declare a constant a test uses first
- Task: roadmap #18 (`docs/sessions/session-log.md`).
- Tool: `create_file` of `SessionLogTest`, which used `SessionLog.MAX_RESULT`;
  refused ("MAX_RESULT cannot be resolved or is not a field").
- What happened: resent in a batch with `create_file` of a `SessionLog` that
  held the constant first. One extra call, and the test resent whole.
- Idea: a stub for a static field read as `T.NAME` (typed from the use, as
  methods are, with a default value) when the type is the project's.
- Follow-up: done: `T.NAME` gets a static field stub (roadmap #107, 2026-11-06).

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
- Follow-up: done (roadmap #108, 2026-11-06): the later step's import fix removes it.

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
- Follow-up: the ambiguous import is done (roadmap #105, 2026-11-05); a deleted declaration
  still used is refused before the gate, naming the uses (#139).

## 2026-11-03: stubs declared a local assertEquals for a missing static import
- Task: roadmap #9, a `JournalTest` method using `assertEquals` when the
  file imported only `assertFalse`.
- Tool: `add_declaration` declared a stub `JournalTest.assertEquals(String,
  String)` instead of importing `Assertions.assertEquals` statically. A
  second batch deleted the stub and added the import with `replace_text`.
- Idea: before declaring a stub method on the enclosing type, look for a
  static import of the same class as an existing static import
  (`org.junit.jupiter.api.Assertions.*`) that has the method.
- Follow-up: done: `Imports.staticImports` (#43, golden `imports_static_method`).

## 2026-11-03: no tool moves golden directories
- Task: roadmap #9: eight `rename_local_*` golden cases renamed to `rename_*`,
  and two copied into new cases.
- Tool: by hand, `git mv` and `cp -r`. Compositions tried: `create_file` of
  each file under the new name and then deleting the old (no tool deletes a
  file that is not Java). gorefactor has a file move with copy for this
  ("testdata/old_case to testdata/new_case renames a golden case").
- Idea: a `move_file` (with `copy`) for fixtures and docs, as gorefactor's.
- Follow-up: done: `move_file` with `copy` (#137).

## 2026-11-03: session report counts reads as hand writes
- Task: roadmap #9, `bin/session-report` for the record.
- What happened: `grep` commands with `<` or `>` in the pattern
  (`List<Path>`) were listed under "by hand", and a Python heredoc that
  edited `docs/tool-contract.md` was counted as "44 lines of Java". The
  rename sent to the branch's jar over stdin is not counted as a step.
- Idea: count a Bash command as a write only when a redirect target or a
  writing command names a file, not for `>` inside quotes; count lines of
  Java only for `.java` targets.
- Follow-up: done (#39).

## 2026-11-03: an import removed by one edit, refused as ambiguous by the next
- Task: roadmap #10 (`docs/sessions/gate-cost.md`).
- Tool: `replace_declaration` of `TypeCheck.errors` with a body that no longer
  used `Path` removed `import java.nio.file.Path`; the next edit used `Path`
  again and was refused: "Path could be com.networknt.schema.path.Path,
  java.nio.file.Path or org.eclipse.core.runtime.Path". Resent with `imports`.
- Idea: when a name is ambiguous, prefer `java.*` (or what the file imported
  before, or what its other imports' packages suggest) over library classes,
  and say which was chosen in the result.
- Follow-up: done (roadmap #105, 2026-11-05).

## 2026-11-03: overload name with a generic parameter typed by hand
- Task: roadmap #36 (`docs/sessions/overload-generic-params.md`).
- Tool: `replace_declaration` named `A.b(Map<String, Integer>)` was refused:
  "has no member b with parameters (Map<String, Integer>). Its members are
  b(Map<String,Integer>)". The fix, `Declarations.topLevelCommas`, was written
  with the Edit tool, not with `replace_text` or `replace_declaration` on this
  repo's own source. Those tools cover it once the server runs; the choice was
  not tested against them.
- Follow-up: done (#32); the hand edit was before the server ran in that session.

## 2026-11-03: stub import picks from another file, found by reading
- Task: roadmap #37 (`docs/sessions/stubs-uses-picks.md`).
- Tool: none. Found by reading `Stubs.Round` after the review; no call was
  refused. `create_file` of a class calling two stubs with `java.awt.List` and
  `java.util.List` wrote `import java.util.List;` into the file that needed
  `java.awt.List`, and was refused only as a side effect of the call.
- Edit: `Stubs.java` changed with the Edit tool, not `replace_text`, the same
  gap as #36's entry above.
- Follow-up: done (#33).

## 2026-11-03: the server's own source edited with the Edit tool
- Task: roadmap #38 (`docs/sessions/maven-classpath-note.md`).
- Tool: none used. `SourceProject.java` and `Gate.java` were changed with the
  Edit tool; `replace_text` on those files would have made the same change.
  The same gap as the #36 and #37 entries above.
- Follow-up: no gap to fix: the session had no java-refactor-mcp tools; the tools
  named exist now (triage 2026-11-05).

## 2026-11-03: six server files edited by script for one refusal wording
- Task: roadmap #26 (`docs/sessions/path-refusal-one-place.md`).
- Tool: none used. `SourceProject`, `Extract`, `Signatures`, `Declarations`,
  `Edits` and `Inspect` were changed by Python scripts that replaced anchor
  text. `replace_text` on those files would have made the same edits.
- Follow-up: no gap to fix: the session had no java-refactor-mcp tools; the tools
  named exist now (triage 2026-11-05).

## 2026-11-03: static state removed by script, six server files
- Task: roadmap #33 and #43 (`docs/sessions/no-global-state.md`).
- Tool: none used. `Journal`, `Gate`, `Imports`, `TypeCheck`, `Server`, `Tools`,
  `Batch` and `Edits` were changed by Python scripts that replaced anchor text.
  `replace_text` would have made the same edits.
- Follow-up: no gap to fix: the session had no java-refactor-mcp tools; the tools
  named exist now (triage 2026-11-05).


## 2026-11-03: line helper call sites changed by script across six files
- Task: roadmap #27 (`docs/sessions/one-line-helper.md`).
- Tool: none used. `Declarations`, `Imports`, `Edits`, `Tools`, `Rename` and
  `Extract` were changed by a Python script (regex renames and exact anchor
  replacements); `Lines.java` and `LinesTest.java` were written with Write.
  `replace_text` would cover the call-site renames, one file per call; a
  `batch` of six `replace_text` edits with check "end" would have made the
  whole change in one call. The script's anchors that matched more than once
  were caught by count assertions, not by the tool.
- Follow-up: no gap to fix: the session had no java-refactor-mcp tools; the tools
  named exist now (triage 2026-11-05).
## 2026-11-03: a stub assertFalse again, for a missing static import
- Task: roadmap #45 (`docs/sessions/session-report-counts.md`).
- Tool: `add_declaration` of three tests, one calling `assertFalse`, with
  only `assertEquals` statically imported: it declared a stub
  `SessionReportTest.assertFalse(boolean, String)`, as it did for
  `assertEquals` in #9. Deleted with `replace_declaration`, import added with
  `replace_text`, in one batch.
- Idea: before declaring a stub method in a class, look for a static method
  of that name in the classes the file already imports statically (here
  `org.junit.jupiter.api.Assertions`), and import it instead.
- Follow-up: done: `Imports.staticImports` (#43).

## 2026-11-03: Family.of prefilter edited by hand
- Task: roadmap order 44 (`docs/sessions/reuse-gate-parse.md`).
- Tool: none used. `Signatures.java` (`Family.of`, a new `simpleName`) was
  changed with the Edit tool; the java-refactor-mcp tools were not in this
  session's tool list. `replace_text` would have made both edits.
- Follow-up: no gap to fix: the session had no java-refactor-mcp tools; the tools
  named exist now (triage 2026-11-05).

## 2026-11-03: nested class moved to its own file by script
- Task: roadmap order 25 (`docs/sessions/split-extract.md`).
- Tool: none used. `Extract`'s nested `Extraction` (660 lines) was copied to
  `Extraction.java` by a Python script, deleted from `Extract.java`, and its
  helper calls were qualified by regular expression. No tool moves a type or
  member to a new file: a `create_file` needed the 660 lines typed out, and
  `replace_declaration` with empty content only deletes. A `move_type` tool
  (move a nested class or member to a new file, fixing references and
  imports) would have done it.
- Follow-up: done: `move_type` (#47).

## 2026-11-03: parser options moved into Jdt by hand
- Task: review item 28 (`docs/sessions/one-parser-config.md`).
- Tool: none used. `Jdt.java` and `Declarations.java` were changed with the
  Edit tool; the java-refactor-mcp tools were not in this session's tool
  list. `replace_text` would have made each edit.
- Follow-up: no gap to fix: the session had no java-refactor-mcp tools; the tools
  named exist now (triage 2026-11-05).

## 2026-11-03: gated-edit call sites changed by hand
- Task: roadmap order 32 (`docs/sessions/edit-pipeline.md`).
- Tool: none used. `Edits.java`, `Declarations.java` and `Extraction.java`
  were changed with the Edit tool; the java-refactor-mcp tools were not in
  this session's tool list. `replace_text` would have made each edit, and a
  `batch` of them with check "end" the whole change in one call.
- Follow-up: no gap to fix: the session had no java-refactor-mcp tools; the tools
  named exist now (triage 2026-11-05).

## 2026-11-03: Gate.deferred fix and its test made by hand
- Task: roadmap order 41 (`docs/sessions/gate-deferred-nesting.md`).
- Tool: none used. `Gate.java` and `GateTest.java` were changed with the Edit
  tool; the java-refactor-mcp tools were not in this session's tool list.
  `add_declaration` would have written the test and `replace_text` the fix.
- Follow-up: no gap to fix: the session had no java-refactor-mcp tools; the tools
  named exist now (triage 2026-11-05).

## 2026-11-03: end-of-batch check moved into Gate by hand
- Task: roadmap order 24 (`docs/sessions/gate-before-after.md`).
- Tool: none used. `Gate.java` and `Batch.java` were changed with the Edit
  tool. A `move` of the check's steps into `Gate` has no tool; `replace_text`
  would cover each edit.
- Follow-up: open: moving members to a class in another file (roadmap #109).

## 2026-11-03: rename column refusal and golden written by hand
- Task: roadmap order 37 (`docs/sessions/refuse-column-below-one.md`).
- Tool: none used. `Tools.java` and `Rename.java` were changed with the Edit
  tool, and the golden case was copied with cp. `replace_text` would cover
  the source edits; `create_file` the golden files.
- Follow-up: no gap to fix: the session had no java-refactor-mcp tools; `move_file`
  copies a golden case now (#137).

## 2026-11-03: engine-error path and undo change made by hand
- Task: roadmap order 35 (`docs/sessions/server-engine-error.md`).
- Tool: none used. `Server.java`, `Tools.java`, `ServerTest.java` and
  `JournalTest.java` were changed with the Edit tool; `replace_text` and
  `add_declaration` would have covered the edits and the tests.
- Follow-up: no gap to fix: the session had no java-refactor-mcp tools; the tools
  named exist now (triage 2026-11-05).

## 2026-11-03: file writers consolidated by hand
- Task: roadmap order 40 (`docs/sessions/file-writers-one-place.md`).
- Tool: none used. `Gate.java`, `Journal.java` and `GateTest.java` were changed
  with the Edit tool and sed. `replace_text` would cover the source edits; a
  `move` of a method between two classes has no tool.
- Follow-up: open: moving a method to a class in another file (roadmap #109).
## 2026-11-03: create_file fixes imports in a file under no source root
- Task: `move_type` golden cases (`docs/sessions/move-type.md`).
- Tool: `create_file` of `src/test/resources/golden/move_type/out/com/example/Shop.java`
  (expected output, under no source root) was refused: "Line could be
  javax.sound.sampled.Line or org.eclipse.jface.text.Line". The `Line` it
  meant is a sibling golden file that no parse can see. Passing `imports`
  would have added an import the expected output must not have. Written by
  heredoc instead.
- Idea: fix imports (and type-check) only for `.java` files under a source
  root, as the gate already counts errors only there.
- Follow-up: done (#53).

## 2026-11-03: deleting a helper still called in the same file declares a stub
- Task: roadmap order 36 (`docs/sessions/names-one-class.md`): `Stubs.label`
  deleted with `replace_declaration`, its callers in `Stubs.Round` still using it.
- Tool: `replace_declaration` deleted `Stubs.label` and then declared
  `Stubs.Round.label(ITypeBinding)` back in the file, as a "not implemented"
  method for the calls that remained. No refusal. Removed with a second
  `replace_declaration` after the callers were repointed.
- Idea: a deletion whose callers remain should refuse and name them, as
  `replace_declaration` does for a deleted method used elsewhere. The session
  notes say a member the edit removed is not declared again; here it was, and
  the cause is not yet known (not checked: whether the callers were in the
  same file as the deletion's target, or the rule covers only methods).
- Follow-up: done: safe delete refuses and names the uses (#139).

## 2026-11-03: no stub for a new overload of an existing method
- Task: `docs/sessions/cli-cache.md`.
- Tool: `add_declaration` of a test calling `SourceProject.load(dir, cache)`
  was refused, "The method load(Path) in the type SourceProject is not
  applicable for the arguments (Path, Path)", where a new method name would
  have got a stub. Answered with a batch: the overload first, then the test.
- Idea: declare a stub for a call whose arguments fit no overload of a
  project method, as for an undefined method, typed from the call.
- Follow-up: done (#57).

## 2026-11-03: a stub called in an enhanced for is not iterable
- Task: `docs/sessions/narrow-check.md`.
- Tool: `add_declaration` of a test with `for (Path p : TypeCheck.lastParsed(project))`
  was refused: "Can only iterate over an array or an instance of
  java.lang.Iterable". The stub's return type was not inferred from the loop.
  Resent with `List<Path> parsed = TypeCheck.lastParsed(project);` first.
- Idea: type a stub used as an enhanced for's expression as `List<T>`, T the
  loop variable's type.
- Follow-up: done (#68, #69).

## 2026-11-04: a lambda argument to an undefined method gets an Object stub
- Task: `docs/sessions/stub-parse-refusal.md` (a probe, not a change).
- Tool: `add_declaration` of a method calling an undefined `store(() -> 1)`
  was refused: "The method store(Object) in the type Basket is not applicable
  for the arguments (() -> {})", and "The target type of this expression must
  be a functional interface". The stub's parameter was typed `Object` from the
  argument, which is not a functional interface.
- Idea: type a stub's parameter for a lambda argument as a functional
  interface, from the lambda's arity (`Function`, `Supplier`, ...), or refuse
  with the names the model should try, rather than an `Object` stub.
- Follow-up: done (#74).

## 2026-11-03: stubs hid a wrong move_type
- Task: `docs/sessions/move-type-inherited.md`.
- Tool: `move_type` of a nested class that used `Helper`, a member type the
  outer class inherits, left `Helper` unqualified in the new file. Nothing
  declared it there, so the stubs declared an empty `com/example/Helper.java`,
  and the gate saw no new error: the move applied, wrong.
- Idea: declare stubs only for edits (code the model wrote), not for a
  refactoring's own output, which changes no behavior and so needs none; a
  name a refactoring leaves unresolved is its bug, and should be refused.
- Follow-up: done: stubs only for edits (#76).

## 2026-11-04: a deleted inherited method was still stubbed

A method deleted from a superclass in another file was still stubbed in a subclass that inherits it (`docs/sessions/stub-after-delete-inherited.md`). The Java was written by Edit (`removedAround` gained a supertype check, `removedInSupertypes` added), not by a tool: `replace_declaration` would fit `removedAround` and `add_declaration` the new method. The tool server was not loaded in that session, so no tool was tried; the gap is the session, recorded in the record.
- Follow-up: done (#75); the hand edit was the session's, as the entry says.

## 2026-11-04: a test helper's stub went to src/main, and no tool deletes a file
- Task: roadmap #61 (`docs/sessions/stress-restore.md`).
- Tool: `create_file` of `StressSnapshotTest` declared the class it uses,
  `StressSnapshot`, as a stub in `src/main/java`. The class is a test
  helper. Moving it took a batch (delete the declaration, create the class
  in `src/test/java`), then `rm` of the file left with only its package
  line: `replace_declaration` with empty content deletes a declaration, not
  its file, and no tool deletes a file.
- Idea: a stub for a class that only a test file uses goes in the test
  tree; `replace_declaration` deletes the file when it removes its only
  type.
- Follow-up: done: stubs in the user's source root (#96), and the last type's
  deletion takes its file (#94).

## 2026-11-04: extract_variable's golden cases were written by hand (before the tool existed)

The first of the ten higher-level tools (`docs/sessions/extract-variable.md`). Its
three golden cases and its implementation were written with Write and Edit, since
the tool did not exist yet. The line numbers in two cases were wrong on the first
run (the statement was one line lower than written), which the golden run caught.
The tool that would have written the cases: none yet; a golden-case helper that
numbers the line of a text would have avoided the mistake, which is a test
convenience rather than a refactoring, so it is not on the roadmap.
- Follow-up: no gap to fix: the tool being built cannot write itself (triage
  2026-11-05).

## 2026-11-04: inline_variable's cases and implementation written by hand (before the tool existed)

The second of the ten higher-level tools (`docs/sessions/inline-variable.md`). Its
goldens and `InlineVariable` were written with Write, since the tool did not exist.
Nothing failed on the first real run, but the goldens were run against the refusal
text they name, so the phrases are checked. The tool that would have written them:
none yet; the case is the tool being built.
- Follow-up: no gap to fix: the tool being built cannot write itself (triage
  2026-11-05).

## 2026-11-04: inline_function's cases and implementation written by hand (before the tool existed)

The third of the ten higher-level tools (`docs/sessions/inline-function.md`). Its
goldens and `InlineFunction` were written with Write, since the tool did not exist.
The gate caught the first wrong substitution (the whole statement, not its expression)
as a syntax error; the golden run caught the blank line left after the method. The tool
that would have written them: none yet; the case is the tool being built.
- Follow-up: no gap to fix: the tool being built cannot write itself (triage
  2026-11-05).

## 2026-11-04: the new refactorings change behavior and still type-check
- Task: `docs/sessions/stress-new-tools.md` (the five new refactorings in
  `RefactoringStressTest`).
- Tool: the stress run found no failure in 220 calls on Commons Lang and
  Math, but it sees only compile errors. Probes on a scratch project found
  seven edits that applied, type-checked, and changed what the code does:
  `inline_function` dropped an argument with effects, reordered two,
  lost a conversion (`3 / 2`) and rebound `FACTOR` to the caller's field;
  `extract_variable` moved `i * 2` ahead of `i++` and let a new local hide
  a field; `inline_variable` put a field where a local hides it; `pull_up`
  made a sibling subclass run the pulled body. Separately, `pull_up` into a
  one-line `{}` body wrote a syntax error.
- Idea: roadmap #67-#70. For the name cases, one mechanism: check that
  every name in moved text resolves to the same binding after the edit.
- Also: an ambiguous-import refusal names one simple name per call
  (`Expression`, then `MethodInvocation`), and suggested JUnit's
  `org.junit.jupiter.engine.execution.MethodInvocation` in a file that
  imports only `org.eclipse.jdt.core.dom` classes. Listing every
  ambiguous name, and preferring a package the file already imports from,
  would have saved two calls.
- Follow-up: done: roadmap #67-#70 and `NameCheck` (roadmap #77, #158-#160); the
  ambiguous-import part is done (roadmap #105, 2026-11-05).

## 2026-11-04: change_type's cases written by hand (before the tool existed)

The fourth of the ten higher-level tools (`docs/sessions/change-type.md`). The goldens and
`ChangeType` were written with Write. The first success case was wrong (a getter returning
`int`), and the gate refused it, which the golden run showed. The tool that would have written
the cases: none yet; the case is the tool being built.
- Follow-up: no gap to fix: the tool being built cannot write itself (triage
  2026-11-05).

## 2026-11-04: add_supertype written by hand (before the tool existed)

The fifth of the ten higher-level tools (`docs/sessions/add-supertype.md`). The class and the
`Tools` registration (the `Def`, its `ALL` entry, the handler) were written by hand: the tool does
not exist yet to write its own body. Once the server runs, `create_file` writes the class and
`add_declaration` adds the `Def` field, with a `replace_text` for the `ALL` list. The insertion
point for an interface's list and a class's `extends` before `implements` was not in the plan; the
goldens showed both.
- Follow-up: no gap to fix: the tool being built cannot write itself (triage
  2026-11-05).

## 2026-11-04: move_statement written by hand (before the tool existed)

The sixth of the ten higher-level tools (`docs/sessions/move-statement.md`). The class and its
`Tools` registration were written by hand, as the earlier new tools were: no tool writes a new
tool. The independence rule was the user's choice (strict by default, names_only as a flag); the
text swap itself is a composition that could be written with `replace_text` over the two lines,
which the refactoring now does without it.
- Follow-up: no gap to fix: the tool being built cannot write itself (triage
  2026-11-05).

## 2026-11-04: invert_if written by hand (before the tool existed)

The seventh of the ten higher-level tools (`docs/sessions/invert-if.md`). The class and registration
were written by hand, as the earlier new tools were. The negation rules were the user's choice
(simplify comparisons; refuse ordering comparisons on floats).
- Follow-up: no gap to fix: the tool being built cannot write itself (triage
  2026-11-05).

## 2026-11-04: remove_else written by hand (before the tool existed)

The eighth of the ten higher-level tools (`docs/sessions/remove-else.md`). The class and registration
were written by hand, as the earlier new tools were. The rule (only when the then-branch cannot
finish normally) keeps the code's meaning; the dedent is the one extra step beyond the plan.
- Follow-up: no gap to fix: the tool being built cannot write itself (triage
  2026-11-05).

## 2026-11-04: move_member written by hand (before the tool existed)

The ninth of the ten higher-level tools (`docs/sessions/move-member.md`). The class and registration
were written by hand. The receiver parameter was the user's choice; the target limited to the same
file is a default this PR took and the record names.
- Follow-up: no gap to fix: the tool being built cannot write itself (triage
  2026-11-05).

## 2026-11-04: encapsulate_field written by hand (before the tool existed)

The tenth of the ten higher-level tools (`docs/sessions/encapsulate-field.md`). The class and
registration were written by hand. The writes the tool refuses (compound, ++, expressions) are where
a composition of `replace_text` would be needed: a hand edit for each, which the refusal names.
- Follow-up: no gap to fix: the tool being built cannot write itself (triage
  2026-11-05).

## 2026-11-04: effect-order refusals written by hand

The effect-order probes (`docs/sessions/stress-new-tools.md`) were found by the stress run, but
the fix was written by hand, not by a tool: the refusals for the seven cases were added with Edit
to `InlineFunction`, `ExtractVariable` and `InlineVariable` in one change
(`docs/sessions/effect-order.md`). No tool was loaded in that session, and no tool adds a refusal. Before the
change, each of the three tools accepted a rewrite that compiled and changed behavior. A
refusal-only fix is the chosen rule; a tool that rewrote the call (for example, a temporary for the
dropped argument) would need the evaluation order named first, which no tool gives.
- Follow-up: no gap to fix: the session had no java-refactor-mcp tools; the tools
  named exist now (triage 2026-11-05).

## 2026-11-04: probing uncommitted server code from the command line
- Task: roadmap #73 (`docs/sessions/preserve-exceptions.md`).
- Tool: `bin/jrefactor` rebuilds its jar only for a commit newer than the
  jar, so probing the branch's uncommitted change took a temporary commit and
  a soft reset.
- Idea: a `--rebuild` option (or `JREFACTOR_REBUILD=1`) that builds from the
  working tree first.
  Done: `bin/jrefactor --rebuild` (roadmap #79, #157, 2026-11-04).

## 2026-11-04: a batch removes an import a later step needs
- Task: `docs/sessions/inline-function-instance.md` (and `refactoring-shape.md`).
- Tool: in one batch, a step that removed the last use of `Modifier` (and, in
  #71, of `Annotation`) also removed its import; a later step that used the name
  again was refused as ambiguous, with a suggestion from another package.
- Idea: in a batch, when a name a step needs was imported by the file before
  the batch, choose that import rather than refuse.
- Follow-up: done (roadmap #105, 2026-11-05).

## 2026-11-04: a refused batch leaves the directories its create_file steps made
- Task: `docs/sessions/same-meaning-check.md`.
- Tool: a batch with `create_file` steps for a new golden case was refused at
  the end (an unhandled exception in another step); the files were removed,
  but their new directories stayed, empty, and `GoldenTest` then failed on the
  case directory with no `args.properties`.
- Idea: the journal removes directories that a refused or undone call created
  and left empty.
  Done: `Journal.put` (2026-11-04, `docs/sessions/journal-empty-dirs.md`).

## 2026-11-05: move_file drops the executable bit
- Task: `docs/sessions/ab-warehouse.md`.
- Tool: `move_file` with `copy` copied `mvnw` into the A/B fixture as text
  through the journal, without its mode, so `./mvnw` was "permission denied"
  until a `chmod +x` by hand.
- Idea: the journal keeps a file's permissions when it moves or copies it.
  Done: `Journal.copyMode`, and the journal's modes for undo (2026-11-05,
  `docs/sessions/move-file-mode.md`).

## 2026-11-05: a stub for a call in an anonymous class of a static method is an instance method
- Task: `docs/sessions/namecheck-positions.md`.
- Tool: `replace_text` with a call of the new `at(int)` inside an `ASTVisitor`
  in a static method of `NameCheck` declared the stub without `static`, and the
  gate refused the edit ("Cannot make a static reference to the non-static
  method"). Resent as a batch with the declaration first, check `end`.
- Idea: a stub is static when the call's nearest enclosing method of the
  target class is static, looking through anonymous classes and lambdas.
  Done: `Stubs.inStaticContext` (2026-11-05, `docs/sessions/stub-static-anonymous.md`).

## 2026-11-05: bin/jrefactor @file takes the arguments only
- Task: `docs/sessions/shared-helpers.md`.
- Tool: `bin/jrefactor @steps.json` without a tool name was read as a tool named
  `@...`; with `batch`, a JSON object holding `"tool": "batch"` was refused
  ("batch does not take tool").
- Idea: a JSON object with a `tool` key names the tool, so `bin/jrefactor @file`
  works alone.
  Done: `Cli.run` and `Cli.arguments` (2026-11-05, `docs/sessions/cli-tool-key.md`).

## 2026-11-05: a release on every push (the user, as gorefactor's #555)

- By hand: the release job was appended to `.github/workflows/ci.yml` with a
  shell redirect; `replace_text` has no way to add text at the end of a file
  (gorefactor's has `append`), and the job was 80 new lines.
- Through the tools: `Server.version()` was added returning the constant, used
  in `serverInfo`, then its test written and failing, then the method replaced
  to read the manifest and the constant deleted (a safe delete).
- Idea: `replace_text` with `append`, as gorefactor's.
  Done: `replace_text` with `append` (2026-11-05,
  `docs/sessions/replace-text-append.md`).

## 2026-11-05: friction-log entries appended by heredoc
- Task: the A/B and fold work of `docs/sessions/ab-warehouse.md` and after.
- Tool: three entries were appended with `cat >> docs/friction-log.md`, since
  `replace_text` could not add at the end of a file; another session hit the same
  for a CI job (its own entry, PR #182).
- Done: `replace_text` with `append` (2026-11-05,
  `docs/sessions/replace-text-append.md`); this entry was appended with it.

## 2026-11-05: the read and write guards (roadmap #15, #104)

Nothing by hand. `Guard` was created empty with `create_file`, so the test written
next got its stub (a test calling a method of a class nobody declares gets no stub
for the method), then the stub replaced with `replace_declaration` and the rest
added with `add_declaration`. The tokenizer is a port of gorefactor's
`internal/hook.Commands`; a shared contract for hooks is not worth one yet.
- Follow-up: none needed: nothing by hand.

## 2026-11-05: NameCheck can miss a capture when two files declare a name at one offset
- Found while fixing #170 (`docs/sessions/namecheck-positions.md`), not by a run:
  a known limit, logged at the user's request.
- `NameCheck.meanings` records each name's meaning by its declaration's offset
  (`@123`), to compare declarations in anonymous classes, whose binding keys hold
  source positions an earlier edit shifts. The offset is not tied to a file. When
  moved text comes from one file into another, and the second file has a closer
  declaration of the same name at the same offset as the one the name meant in the
  first, the capture is accepted.
- Unlikely (same identifier, same offset, two files, and a capture), and no case
  has been seen; but a check that accepts a capture by coincidence is wrong.
- Idea: key the position by file too (`path@offset`): `meanings` takes each
  fragment's file (the units come from `Holding` or `Jdt.parseAll`, which know
  their paths), and `require` compares the mapped-back position in the file the
  declaration is in.
  Done: `Jdt.FILE` on each unit, `NameCheck.at(file, offset)` (2026-11-05,
  `docs/sessions/namecheck-by-file.md`).

## 2026-11-05: session report counts a bin/jrefactor call as by hand
- Task: `docs/sessions/probe-test.md`.
- Tool: a batch of 66 `create_file` steps sent through `bin/jrefactor @file` was
  reported by `bin/session-report` as one Bash write by hand, though every step went
  through the tools.
- Idea: the report counts a Bash command that runs `bin/jrefactor` as tool steps
  (its tool, or the batch's steps), as it does the MCP calls.
  Done: `SessionReport.cli` (2026-11-05, `docs/sessions/session-report-cli.md`).

## 2026-11-05: rename refuses a member of a second top-level class of a file
- Task: `docs/sessions/probes-remaining.md`.
- Tool: `rename` of `Base.a`, where `class Base` is a second top-level class of
  `p/Main.java`, refused: "the field Base.a is declared in p.Base, not in this
  project". It looks for the type's file by its name (`p/Base.java`). Safe (a
  refusal), but such members cannot be renamed; the probes that meant to test a
  field hidden by a subclass and a method renamed into an override test this
  refusal instead.
- Idea: find the declaring file through the binding (`Holding` by the type's name,
  then `findDeclaringNode` by key), as `move_type` with `package` does.
  Done: `Signatures.projects` accepts a type parsed from source (2026-11-05,
  `docs/sessions/rename-secondary-and-nested.md`).

## 2026-11-05: the read guard refused a grep -c and a hidden path
- Task: the probe and rename work of 2026-11-05.
- Tool: the read guard refused a `grep -c DEBUG` of a `.java` file (a count, no text)
  and a `cat` of golden Java through a shell variable path; each cost a retry. Three
  other refusals that day were `grep` printing Java lines, which the rule means.
- Done: `Guard.showsNoText` and the hidden-path reason (2026-11-05,
  `docs/sessions/read-guard-false-refusals.md`).

## 2026-11-05: the guard's grep relaxation reverted; the search is in inspect
- Task: the read-guard follow-up to the entry "the read guard refused a grep -c and
  a hidden path" above.
- Tool: allowing `grep -c/-l/-q` of Java (#201) removed the friction without
  routing the search through a tool, and `inspect | grep` saved nothing over grep.
- Done: `inspect` `pattern` and `names` (#202, #203), and the guard refuses a grep
  of Java again (`docs/sessions/read-guard-to-inspect.md`); gorefactor the same.

## 2026-11-05: replace_text matched old inside a line's indentation
- Task: `docs/sessions/ambiguous-imports.md`, a line added to `add_declaration`'s
  description.
- Tool: `replace_text` with `old` written at 8 spaces matched a line at 10 (the 8
  spaces are inside the 10), so the new lines went in at 8; Spotless then
  re-indented the whole text block, and `ServerTest`'s runs-of-spaces check failed.
  Three more calls to put the block back.
- Idea: an `old` that starts with spaces matches only from the start of a line, or
  the refusal says it matched inside the indentation; with roadmap #106.

## 2026-11-05: a class stub declared from a test lands in the test tree
- Task: answering how to create a new Java file test first (a probe on a scratch
  Maven layout, not a change).
- Tool: `create_file` of `src/test/java/p/CartTest.java`, which uses a `Cart` nothing
  declares, declared the stub class in `src/test/java/p/Cart.java`. A production class
  belongs in `src/main/java`, and no tool moves it there: `move_file` refuses Java under
  a source root, and `move_type` with `package` stays in the file's source root.
- Composition that works: `create_file` of an empty `class Cart {}` in `src/main/java`
  first; then the test's member stubs go into it.
- Idea: a class stub declared from a test file goes in the main source root (same
  package), as a test's subject does; or `move_type` takes another source root.
  Done: roadmap #113 (2026-11-06), test doubles and helpers excepted.

## 2026-11-05: A/B on the shop tasks: an assertion stubbed, extract_struct on a record
- Task: `docs/sessions/ab-shop-tasks.md` (the skeleton-only arm's transcripts).
- Tool: `add_declaration` of test methods into a class made by `create_file` with
  `declaration` (no imports) declared `assertEquals(boolean, boolean)` as a "not
  implemented" stub on the test class instead of a static import from JUnit; the model
  then spent six calls removing it. Idea: an unresolved call named like an assertion, in a
  file under the test root with JUnit on the classpath, imports it statically. Done:
  roadmap #117 (#219, 2026-11-05).
- Tool: `extract_struct` with `fields` on the record `Order` refused ("Order is not a
  top-level type ... that resolves"); the model had reached for a transformation to make
  `Address`. It then used `extract_struct` on `Shipping.validAddress`'s parameters and
  `move_type`, and edited `Order` by hand. Done: roadmap #118 (#220, 2026-11-05).

## 2026-11-05: a batch refused for one name resent whole (extract-struct-record)
- Task: roadmap #118, `docs/sessions/extract-struct-record.md`.
- Tool: a batch of a `replace_text`, a 236-line `add_declaration` (`ExtractStruct.components`)
  and a `Tools.java` `replace_text` was refused twice: first for a duplicate local (`declared`,
  one line), then for the third step's `old` two spaces too deep (the separator trap again,
  roadmap #106). Each refusal sent the 236 lines again; three sends of new behavior for two
  one-line mistakes. Sequences tried: none fits; a batch is all or nothing, and a step
  cannot be corrected by itself. Idea: roadmap #110 (a refusal that offers the fix as a
  later step), or a batch that keeps the steps before the refused one for a resend of the
  rest. Done (2026-11-06, batch-retry): `retry: true` resends a refused batch with only the
  steps that change.

## 2026-11-06: the ambiguous-import rule chose the wrong Pattern (test-stub-main-root)
- Task: roadmap #113, `docs/sessions/test-stub-main-root.md`.
- Tool: a batch adding `Pattern.compile(...)` to `Stubs`, which imports many
  `org.eclipse.jdt.core.dom` classes: #105's rule took `org.eclipse.jdt.core.dom.Pattern` (the
  only candidate in a package the file imports from) over `java.util.regex.Pattern`; the type
  check refused the batch ("compile(String) is undefined for the type Pattern"), and the resend
  passed `imports`. One resend; nothing wrong was written.
- Idea: when the package rule's pick does not type-check and another candidate would, take
  that one (the check already runs); or prefer `java.*` when the use calls a member the
  package's class lacks. Done (2026-11-06, #245): what the package's other files import comes
  before the packages rule, and they import `java.util.regex.Pattern`.

## 2026-11-06: add_declaration's after refused an overload by the label it listed
- Task: roadmap #129, `docs/sessions/add-parameter-record.md`.
- Tool: `add_declaration` with `after` `addParameter(SourceProject, Args)` was refused ("no member
  addParameter(SourceProject, Args)") by a refusal that listed that exact label among the
  members; `after` takes a bare name. Resent with another member's name (the batch, 120 lines,
  sent twice).
- Idea: `after` and `before` take the labels the refusal lists (an overload with its parameter
  types). Done (2026-11-06, small-fixes): the label was compared with its spaces kept.

## 2026-11-06: move refused for an import it cannot be told (A/B, shop-duplication)
- Task: the A/B check of the merged list, `docs/sessions/ab-merged-tools.md`.
- Tool: `move` of `Csv.exported` to `Exported` was refused because `Order` could be JUnit's or
  `shop.order.Order`, "Pass imports ... as imports: [\"org.junit.jupiter.api.Order\"]"; resent
  with `imports`, it was refused as "move does not take imports". `move_member` behind it fixes
  imports with no choices. The model wrote the class itself. The suggestion also put the test
  library's class first over the project's own.
- Idea: `imports` on `move`, passed to each route; prefer a project class over a library's.
  Done: roadmap #131 (2026-11-06).

## 2026-11-06: blank final fields before their constructor (A/B, library-reservation)

Both skeleton runs of the reservation task added `Reservation`'s `final` fields with
`add_declaration`, then the constructor; the fields alone did not compile ("The blank final field
book may not have been initialized") and the model sent fields and constructor again as a batch.
The compile refusal could name the operation, as the skeleton refusal does: "declare them with
`constructor: [book, member, due]` in the same batch". No tool does it now; the batch is the
composition, found by the model after a refusal. Record: `docs/sessions/ab-library-skeleton.md`.

Follow-up (2026-11-06, `docs/sessions/final-fields-constructor.md`): the compile refusal now
names `constructor` with the unset fields, in `Gate.refusal`, so every edit's refusal says it.

## 2026-11-06: a test's stubs block `declaration` (A/B, library-notifiers)

Both runs wrote the test first, so `SmsNotifier`, `PushNotifier` and `PagerNotifier` were declared
as stubs: classes with no supertype and package-private methods. `create_file` with
`declaration ... implements Notifier` then refused ("already exists. Set overwrite ...") and
`add_declaration` of `@Override` methods refused ("must override or implement a supertype
method"). The compositions the models found: `add_supertype` then `replace_declaration` of each
method (run 1), or `declaration` with `overwrite` (run 2). Either the refusal could name
`add_supertype`, or `declaration` could replace a file holding only stubs the session declared,
keeping what nothing else typed. Record: `docs/sessions/repeated-constructions.md`.

Follow-up (2026-11-06, `docs/sessions/declaration-names-add-supertype.md`): `create_file`'s
existing-file refusal for a `declaration` now names the `add_supertype` calls for the
supertypes the file's type lacks.

Follow-up (2026-11-06, `docs/sessions/override-names-add-supertype.md`): the compile refusal for
`@Override` methods in a type with no such supertype now names `add_supertype` for it.

## 2026-11-07: replace_declaration of a type passes the skeleton rule (A/B, library-reservation)

Under `JREFACTOR_SKELETON_ONLY=1`, reservation run 2 was refused `add_declaration` of a
constructor that only assigns its fields (the refusal named `constructor`), and then sent the whole
`Reservation` class, that constructor and its getters typed, as `replace_declaration` of
`Reservation`, which the rule does not check. The skeleton checks (`Generated` on the members, and
a type's members typed in place of operations) could run on `replace_declaration` of a type too.
Record: `docs/sessions/ab-hints.md`.

Follow-up (2026-11-07, `docs/sessions/skeleton-replace-declaration.md`): under skeleton-only,
`replace_declaration` of a type now runs the member checks on the new type's members, and the
refusal names the `add_declaration` to send after the replacement, in one batch.

Follow-up (2026-11-07, `docs/sessions/fields-make-constructor.md`): final fields added to a class
with no real constructor now get one written by the edit itself, so the blank-final refusal is
left for classes that have constructors.

Follow-up (2026-11-07, `docs/sessions/stub-constructor-assigns.md`): a constructor declared from a
call whose arguments each fit one field of the class assigns them, so a test written against an
existing class with fields gets a working constructor.

## 2026-11-07: the written constructor and `constructor` collide (A/B, library-value-classes)

After #272, a model that sends final fields and then `add_declaration` with `constructor` in one
batch, as the option, the blank-final hint and the contract teach, gets "Duplicate method
Hold(Member, Book, int)": the fields step already wrote the constructor. Value-classes run 2 lost
three batches and 10 turns to it, and got through by typing the assignments into the test's
constructor stubs with `replace_statements`. A constructor added with the parameter types of one
that only assigns parameters to fields could replace it, as a stub is replaced. Record:
`docs/sessions/ab-constructors.md`.

Follow-up (2026-11-07, `docs/sessions/constructor-replaces-assigning.md`): a constructor added with
the parameter types of one that only assigns fields replaces it, so the fields and then
`constructor` in one batch no longer duplicate it.

Follow-up (2026-11-07, `docs/sessions/skeleton-replace-statements.md`): under skeleton-only,
`replace_statements` in a member's own body is checked on the member it leaves, so a stub
constructor filled with field assignments is refused, naming `constructor`.

## 2026-11-07: code escaped in the CLI's JSON (batch-sections)

Code sent through `bin/jrefactor` in a JSON object (a batch) had to be escaped
as JSON strings; gorefactor found escaping cost resends (its #606). Now the code
goes after the JSON in `@@name` sections, which a string `"@name"` stands for.
Before it, this branch's own two Java edits went as two calls with
`content_file=-`, not one batch, and the second was refused for a line number an
import of the first had moved (`docs/sessions/batch-sections.md`).

## 2026-11-07: a body that uses a field nothing declares yet (A/B, library-card)

Under the skeleton server, both runs filled two stub bodies (`borrowed()`, `borrow()`) with code
reading a field `borrowed` before declaring it, in parallel calls, and both were refused
("borrowed cannot be resolved to a variable"); the field and the bodies were then sent in
parallel, the bodies at lines the field had moved, and refused again. Four lost calls a run. A
method a body calls gets a stub; a bare name gets none (only `T.NAME` does, #107). A field stub for
an unresolved simple name in a member of the class, typed from its use, would have passed the
first calls. Record: `docs/sessions/ab-constructor-stub.md`.

Follow-up (2026-11-08, `docs/sessions/field-stub-bare-name.md`): a bare name nothing declares gets
a field stub in the type around it, typed from its use, so those bodies now pass before the field.

## 2026-11-08: rename of a record refused at every use (split-placement)

Splitting `Declarations` (#32), `rename` of the record `Place` (moved to its own file) to
`Placement`, and then to `Spot`, was refused: "would change what Spot at line 160 ... means: it would
mean the type Spot, not the renamed one". JDT's `SimpleName.isDeclaration()` is false for a record's
name, so `Rename.checkMeanings` found no renamed declaration. Fixed in its own PR
(`docs/sessions/rename-type-argument.md`); the split went on after it. Twelve other
`isDeclaration()` calls may miss a record the same way.

## 2026-11-09: the bare-name stub missed a field named as its getter (A/B, library-card)

Rerunning the skeleton arm after #288, run 2 filled `borrowed()` and `borrow()` before declaring
the field `borrowed`, and was refused as before: `Stubs.Round.removed` took the method stub
`borrowed()` for a field of that name the edit had removed, so no field stub was declared. Two
lost calls. Fixed in the same PR: `removed` tells fields from methods. Record:
`docs/sessions/ab-card-bare-field.md`.

## 2026-11-10: replace_text with batch sections around blank lines lost a table row

- Recording roadmap rows #133-#135 in `docs/design.md`, a `batch` of `replace_text` whose `old`
  was a section starting with two blank lines and `### The 2026-11-04 list`, and whose `new`
  was three table rows, a blank line and that heading. The rows for #134 and #135 landed, but
  the first one, #133, did not, with no refusal; it was added back by a second `replace_text`.
- Found while merging: the row was not lost but joined to the end of row #132's line (`... either || 126 | 133 ...`),
  then put back as its own line, so `main` had it twice until this merge. The section `old`
  probably lost its leading newlines, so `new`'s first line went where the newline after #132 was.
## 2026-11-10: a raw List from JDT in loop_to_stream, by hand

- `UnionType.types()` is a raw `List`, and `List<Type> types = ... ? u.types() : List.of(t)` was
  an unchecked warning (`-Werror`). `replace_statements` of the declaration with `List<Object>` was
  refused, since the `for (Type c : types)` after it no longer compiled; the loop was changed by
  `sed`, then the declaration with `replace_text`. A batch of the two steps with check "end" would
  have done it through the tools.

## 2026-11-10: extract_function and inline around locals declared without a value (LoopToStream)

- Splitting `LoopToStream.run`: `extract_function` of the forEach branch (lines 264-292) was
  refused, "The local variable replacement may not have been initialized": `replacement`,
  `start` and `said` were declared before the range without a value and only assigned in it, and
  the extraction passed them in instead of returning them. Wanted: a local declared without a value
  outside the range, assigned on every path in it and read only after it, is a result.
- Then `inline` of `String replacement = rewrite.text();` (and `start`, `imports`, `said`) was
  refused: "the initializer of replacement calls, assigns or changes a variable, and inlining
  replacement moves it past rewrite.start()". A record's accessor changes nothing; `Effects`
  counts every call. Wanted: a record component accessor (`IMethodBinding` of a record's
  generated accessor) is a read.
- Both were done with edits instead: `add_declaration` of a `Read` and a `Rewrite` record and the
  methods, `replace_statements` of the branch, and `replace_text` for the uses.

## 2026-11-10: a batch step with its arguments beside "tool" (SafeDelete, #322)

- A `batch` whose steps were `{"tool": "replace_text", "file": ..., "old": ..., "new": ...}`
  was refused "step 1 of 5 (replace_text) refused: replace_text needs file." The step had a
  file, beside `tool` rather than in `args`. Wanted: a step with no `args` takes its other keys
  as the arguments, or the refusal says to put them in `"args"`.
- Follow-up: done: the refusal names the keys and shows the step with them under `args`, before
  any step runs (roadmap #196, 2026-11-13).

## 2026-11-10: move of a static method refused for a parameter named receiver

- Moving `MoveMember.mayBeNull(Expression receiver)`, a static method, to a new `NonNull` was
  refused: "MoveMember.mayBeNull uses the name receiver, which move gives the receiver parameter.
  Rename it first." A static method gets no receiver parameter, so the name is free. Done with
  `rename` of the parameter in the same batch, then `move`.

## 2026-11-11: a pushed commit that did not compile stopped bin/jrefactor (#345)

- A commit with a `-Werror` warning (a raw `stream()`) was pushed: `./mvnw verify` failed but
  the shell chain went on with `;`. `bin/jrefactor` rebuilds its jar from the newest commit, so
  every later call failed ("build failed"), and the two fixes (the cast, and the holder-name check
  that took a method `report` for a clash) were made with `sed`. No tool sequence applies: the
  tools themselves could not start. Lesson for sessions: chain verify and commit with `&&`.

## 2026-11-11: move of a recursive static method refused (convert_loop)

- `move` of `LoopToStream.elementOf`, a static method that calls itself, to a new `Loops` class
  was refused: "LoopToStream.elementOf calls itself. A recursive call would go through the
  receiver; move it by hand." A static method has no receiver; its recursive call would name the
  new class. As the 2026-11-10 entry on a parameter named receiver, `move` treats a static method
  as an instance one. Done instead by widening the helpers in place (`replace_text` of
  `private`), which `ConvertLoop` calls as `LoopToStream.elementOf`.

Follow-up (2026-11-11, issue #356, `docs/sessions/move-recursive-static.md`): `move` of a static
method no longer refuses a call of itself or a parameter named `receiver`; the call moves
unqualified.

## 2026-11-11: add_declaration put a Def after the list that names it (convert_loop)

- `add_declaration` of `static final Def CONVERT_LOOP` to `Tools` placed it after `ALL`, whose
  initializer names it, and the gate refused: "Cannot reference a field before it is defined".
  Placement by kind puts a new field after the last field; a static field another static field's
  initializer reads must come before it. Done with `replace_text` before `REMOVE_ELSE`. Wanted:
  `Placement.place` puts a static field before the first static field whose initializer reads it.

Follow-up (2026-11-11, issue #357, `docs/sessions/place-before-reader.md`): a field an edit adds
moves before the field whose initializer reads it, when the check finds the forward reference;
not in placement, since in a batch the reader may name it only in a later step.

## 2026-11-11: batch retry from the CLI (move-recursive-static)

- A batch through `bin/jrefactor` was refused (an `old` misquoted), and the refusal said to send
  `retry: true` with the changed step. The next `bin/jrefactor` run answered "there is no refused
  batch": `Session.refusedBatch` lives in one process, and the CLI starts one per call. The whole
  batch was sent again. The CLI's refusal should not offer `retry`, or the CLI should keep the
  refused batch on disk, as `undo` keeps its journal.

## 2026-11-13: a batch step without `args` (extract-supertype-generic)

- A `batch` sent through `bin/jrefactor` with each step's arguments beside its `tool`
  (`{"tool": "replace_text", "file": ..., "old": ...}`) was refused: "step 1 of 8 (replace_text)
  refused: replace_text needs file." The file was there; what was missing was the `args` object
  around it. Resent with `args`. Wanted: when a step has keys beside `tool` and no `args`, the
  refusal says a step's arguments go in `args`, or the step is read that way.
- Follow-up: done: the refusal names the keys and shows the step with them under `args`, before
  any step runs (roadmap #196, 2026-11-13).

## 2026-11-13: move of a generic method; stubs declared in a nested record (remove-parameter-value-expression)

- `move` of `Introduce.enclosing` (`static <T extends ASTNode> T enclosing(ASTNode, Class<T>)`)
  to a new class was refused: "would add compile errors ... The method enclosing(Expression,
  Class<MethodDeclaration>) is undefined for the type Introduce" (five calls). The calls of a
  generic method were not rewritten to `ParameterValue.enclosing`. Sequences tried: `move` alone
  (refused); `move` of its callers first (refused, they need it). Done by hand: `replace_text`
  dropped `private`, and the method stays in `Introduce`. Wanted: `move` rewrites the calls of a
  generic static method as it does the others'.
- `move` of four static methods into a class with only a private constructor wrote them at a
  four-space step in a two-space file, and qualified the moved methods' calls of each other
  (`ParameterValue.parameterIndex(...)` inside `ParameterValue`). Spotless fixed the first; two
  `replace_text` calls the second.
- `add_declaration` of members that call two helpers not yet declared put the helpers' stubs
  inside the nested record whose method called them. The next batch declared the real helpers in
  the class around it and deleted the stubs with `replace_declaration`, refused: "is used in ...
  line 180. Remove or change those uses first", in either order, since the call binds to the
  nearer stub until it is gone. Done with `replace_text` of the stubs' text in the same batch.
  Wanted: a member with a stub's name and parameter count declared in an enclosing type replaces
  the stub, or the delete of a "not implemented" stub is allowed when the batch declares another
  method the call then resolves to.

## 2026-11-13: the gate passed code that -Werror fails (add-type-parameter)

- `create_file` of `TypeParameters` passed the gate with `new ArrayList<>(target.typeParameters())`
  (a raw `List` from JDT), and `./mvnw package` then failed: "unchecked method invocation",
  `-Xlint:all -Werror`. The gate counts compile errors; this project's build also fails on lint
  warnings. Found only at the next Maven run. A gate option that reports the warnings a change
  adds (JDT has them as problems) would have said so at the edit.
- A `batch` with check `"end"` still refuses a step whose result does not parse: a
  `replace_text` of a method's first lines, completed by the next step's `add_declaration`, was
  refused. Sent as `replace_declaration` of the whole method. Not a defect; the refusal was clear.

## 2026-11-13: batch retry from the CLI (add-type-parameter-members)

- A `batch` sent through `bin/jrefactor` was refused at step 6 (a delete whose use step 7 then
  removed: the steps were in the wrong order), and the refusal said to resend only the changed
  steps with `retry: true`. From the CLI each run is a new session, so the retry was refused:
  "there is no refused batch since the last one passed; send the whole batch." Resent whole,
  reordered. Wanted: the CLI keeps the last refused batch (as `undo` keeps its history across
  runs), or the refusal names `retry` only where it can work.

## 2026-11-13: `batch` `retry` from the CLI (enum-switch-field)

- A refused `batch` sent through `bin/jrefactor` ends with "To fix it without resending the rest,
  batch with retry: true and only the steps that change"; the next `bin/jrefactor` run answered
  "there is no refused batch since the last one passed; send the whole batch": each run is a new
  session, so `Session.refusedBatch` is gone. Resent the whole batch, twice (the second time this was hit on 2026-11-13; see the entry above). Wanted: the CLI keeps
  the last refused batch beside its undo journal, or the refusal says to resend when run from the
  CLI.
- Extracting `EnumField.field` (the edit without `Args`) needed the range's inputs as locals first:
  `extract_function` of the lines as they were would have taken `args`. Tried: `extract_variable`
  of `args.string("before")` then `move_statement` up past four statements, one call per step;
  done instead by one `replace_text` that reordered four lines and named the value, then
  `extract_function`. Not a defect; a `move_statement` with a count would have made it two calls.

## 2026-11-13: extract-variable-parameter-all

- A refused `batch` sent through `bin/jrefactor` offered `retry: true`, and the next run answered
  that there was no refused batch (as on 2026-11-11, "batch retry from the CLI"). The batch was
  edited and sent whole. Still wanted: the CLI keeps the refused batch, or its refusal does not
  offer `retry`.
- `add_parameter` with `value` `false` wrote the new argument on the line of the last one,
  `args.string("as"), false)`, in a call laid out one argument a line; a `replace_text` step in the
  same batch that expected Spotless's layout missed. Not a defect (Spotless re-lays it out); cost
  one resend.

## 2026-11-13: parameter-value-cast

- `add_parameter` (through `extract_variable` `as=parameter`) refused a generic method,
  `static <T> String show(T x)`: "Show has no member show with parameters (Object). Its members
  are show(T), use(int)"; and a parameterized parameter type, `show(Comparable<Integer> x)`: "no
  member show with parameters (Comparable)". `Signatures.label` writes erasures, which
  `Declarations.find` does not match against a type variable or type arguments. Hit writing a
  golden; the golden took `Serializable` instead. Wanted: the family lookup by binding, not by the
  label's text.

## 2026-11-13: stress-new-options

- The stress run of the day's options on Commons Lang (`docs/sessions/stress-new-options.md`)
  found seven bugs, each an issue with its reproduction: the gate errors when the JDT compiler
  throws on changed code (#405); `extract_variable` `as=parameter` cannot find a method with
  generic parameter types (#406, the entry above); `change_type` with `type_parameter` refuses `line` (#407);
  overloads written alike cannot be named, the refusal suggesting the refused name (#408);
  `extract_supertype` `kind=class` under an abstract superclass or one without a no-argument
  constructor (#409); `inline` of an array-initializer local (#410); `rename` beside a single
  static import of a project method (#411). Not fixed in that PR; none pinned by a golden case,
  since `GoldenTest` cannot keep a known-failing case out of the build.
- A `change_type` refusal from the gate reads "changed the type of left of ... would add compile
  errors"; "changing" was meant.
- `spotless:apply` reflowed the stress test's Javadoc between two batches, so a `replace_text` of
  it copied from the first send missed ("old is not in"); copied again from the file. Not a
  defect.

## 2026-11-13: fix-parameter-generic-types

- The `add_parameter` lookup failure logged under parameter-value-cast was issue #406, in
  `Introduce`'s naming of the method (erasures), not in `add_parameter`: fixed there. Nothing by
  hand.
## 2026-11-13: stress-enum-switch

- The enum switch stress run on smallrye/jdk-classfile-backport (`docs/sessions/stress-enum-switch.md`)
  filed seven issues, each with its reproduction: `type_switch_to_method` refuses a bare `throw`
  arm (#435), says a member enum cannot see its enclosing class's statics (#436), and asks for type
  patterns on an `int` switch (#437); a named module is checked as the unnamed one, so sealed
  permits across packages block a rename or move (#438); `inline` of a record's method (#439);
  `move` of a member record whose fields the outer class reads (#440) and of a member of a sealed
  hierarchy with implicit permits (#441), both with the fix text "Rename one of the clashing
  types". `rename` hit #411 again. Not fixed in that PR.
- `batch` refused a first send with each step's arguments at the step's top level ("replace_text
  needs file"); they go under `args`. The refusal could say so when a step has `file` beside
  `tool`.
- Follow-up: done: the refusal names the keys and shows the step with them under `args`, before
  any step runs (roadmap #196, 2026-11-13).

## 2026-11-13: the generator task set (`generator-tasks`)

By hand: the fixture's Maven files (`mvnw`, `.mvn`, `.gitignore`, `pom.xml` with its artifactId
changed) copied from the library fixture with `cp` and `sed`: no tool copies a directory. The
reference solutions used to validate the tasks were applied in scratch copies by a script, not in
the repo. Everything in the repo went through `bin/jrefactor` batches (`create_file`,
`replace_text`).

## 2026-11-13: the A/B arm without build (`no-build-arm`)

By hand: the golden case's `in/` and `steps.json` copied from `batch_build_method` with `cp -r`, and
the wrapper `eval-tasks/jrefactor-ab-server-no-build` made from the skeleton one with `sed` and
`chmod +x`: no tool copies files or sets a file's mode. The Java went through `bin/jrefactor`
(`add_declaration`, `create_file`, `replace_statements`, `replace_expression`, `insert_statements`,
and `extract_variable`, `extract_function`, `inline` and `fold` for the refactoring).

## 2026-11-13: the A/B arm with the aliases published (`alias-arm`)

By hand: the wrapper `eval-tasks/jrefactor-ab-server-aliases` from the no-build one with `sed` and
`chmod +x`. The Java and the docs through `bin/jrefactor`.

## 2026-11-13: named-module

- `create_file` of `module-info.java` (`module m {}`) is refused: "module-info.java does not parse:
  Syntax error on token "module", module expected". `Edits.parses` parses without a unit name, so
  JDT reads it as an ordinary unit. Met while writing a golden case; the case became a unit test
  (`TypeCheckTest.aModuleInfoAddedOrRemovedRecountsEveryFile`), written by `add_declaration`.
  No hand edit of Java.

## 2026-11-13: fix-declared-methods

- `batch` refused a first send with each step's arguments beside `tool` again ("replace_text
  needs file"), the second time today (see stress-enum-switch above): evidence for a refusal that
  says they go under `args`.
- Follow-up: done: the refusal names the keys and shows the step with them under `args`, before
  any step runs (roadmap #196, 2026-11-13).
- `extract_variable` `as=constant` of a string a test wrote twice replaced the first only; the
  second went through `replace_expression` (`new` the constant). `all_occurrences` was not tried.
  Nothing by hand.

## 2026-11-13: the generator A/B write-up (`ab-generators`)

- `bin/jrefactor '{"tool":"replace_text",...,"old":"@old"}'` with the `@@` sections on stdin
  refused "old is not in docs/session-notes.md": with the JSON as an argument, stdin is not read,
  so `old` was the text `@old`. The sections go after the JSON on stdin (`bin/jrefactor -`). The
  refusal could say so when `old` is a bare `@name`. A first `append` passed the text as `append`
  (it takes true, with the text in `new`); its refusal said so. The run's transcripts were tallied
  by a scratch script, not in the repo.

## 2026-11-13: skeleton-build-blocks

- The golden case `insert_statements_skeleton_only_refused_in_block` copied from
  `..._passes_two_statements` with `cp -r`, and that case's `content.txt` and `out` Ledger edited
  with `printf` and `sed`: no tool copies a directory, and the golden `out` files are not edited
  through the tools. The Java went through one `bin/jrefactor` batch.
- A `batch` with `retry: true` in a second `bin/jrefactor` run was refused ("there is no refused
  batch since the last one passed"), though the run before was refused: the CLI does not keep the
  refused batch between runs, while the refusal offered `retry`. Resent whole.

## 2026-11-13: cli-batch-retry

- No hand edit of Java; the fix for the CLI `retry` entries above (roadmap #194). The docs went
  through a Python script, not `replace_text`.

## 2026-11-13: batch-args-beside-tool

- A `bin/jrefactor` batch sent as a JSON argument with its `@@` sections on stdin was refused
  ("content does not parse", `@fields` read as the content): the sections go after the JSON in
  the same input. Resent as `-` from one heredoc. Nothing by hand.

## 2026-11-13: ab-darktable-md

- No Java changed. The darktable variant of the instructions was cut from the project's file by a
  Python script, and the two rules added to both copies by one; the transcripts were tallied by
  scratch scripts, not in the repo.
- My first `git add -A` on another branch (cli-batch-retry) committed the user's untracked
  `examples/darktable-project-CLAUDE.md`; the user kept it in the history. Stage by path.

## 2026-11-13: batch-args-accept

- The golden case was renamed and a new one written by `git mv`, `cp` and heredocs: no tool
  copies a case directory (as skeleton-build-blocks logged). `result.txt` was pinned from a run
  with a placeholder.

## 2026-11-13: change-try

- No Java by hand. The golden cases were written by a script (resources). A batch deleting a
  method before changing its one call was refused; `retry` with `remove` and `after` reordered it.

## 2026-11-13: roadmap-open-issues

- Docs only; the rows inserted by a script.

## 2026-11-13: release-cancel-trap

- Not a tool: #461 merged while #460's run on `main` was building, which cancelled #460's release;
  rerun by hand (v0.33.0). Recorded as a trap in the session notes.

## 2026-11-13: fix-push-down-override

- No Java by hand: `replace_declaration`, `replace_text`, `remove_parameter`. The probe and goldens
  were written by heredocs (resources).

## 2026-11-13: fix-invert-if-dangling-else

- The fix was first written by a Python script before the red run; reverted, the red run seen, and
  rewritten through one `batch` (`replace_text`, two `add_declaration`s). The probe and golden are
  resources, written by heredoc. Nothing by hand in Java.

## 2026-11-13: fix-parameter-argument-exceptions

- Safe delete of `Signatures.pure` and `allPure`, which only call each other: `replace_declaration`
  with empty content refused either one first, naming the other's use. Tried: the two deletes in
  either order in one `batch` (each refused). Done as one `replace_text` of both methods' text.
- `rename` with `name=ChangeSignature.pure` alone refused ("rename needs line"); `inspect` gave the
  line. A `Type.member` name could find the declaration as `replace_declaration` does.

## 2026-11-13: fix-replace-text-empty-old

- No Java by hand: one `replace_text`. The golden case is resources.
- CLI: a JSON argument (`bin/jrefactor '{"...","old":"@old"}'`) with the `@@old` sections on stdin
  took `@old` as the literal text and refused "old is not in ..."; the sections are read only when
  the JSON comes from stdin too (`bin/jrefactor -`, JSON first). Either form could say so.
- CLI: an empty heredoc for `old_file=-` sent an empty `old`, which hit this bug (an
  OutOfMemoryError, files restored); now a refusal.

## 2026-11-13: fix-remove-else-scope

- No Java by hand: one `batch` (`replace_text` twice, `add_declaration`), then `rename`. A `rename`
  step in the batch was refused for want of `line` (the method's line moved within the batch); it
  was dropped by `retry` with `remove` and run after, with the line from `inspect` `pattern`.
  The probe and goldens were written by heredocs (resources).

## 2026-11-13: fix-convert-to-record-equals

- No Java by hand: two `batch`es (`replace_text`, `add_declaration`) sent from a file
  (`@file`), since the code held a `' '` that closed the shell's single-quoted argument; then
  `replace_text` for the class comment. The probe and goldens were written by heredocs.

## 2026-11-13: fix-loop-receiver-binding

- No Java by hand: one `batch` from a file (`replace_text`, `replace_declaration`, `add_declaration`
  across `LoopToStream` and `ConvertLoop`), then `remove_parameter` for the parameter it left unused.
  The probes and goldens were written by heredocs.

## 2026-11-13: fix-new-override

- `move` of `FunctionToMethod.clash` and `.below` to the new `Overrides` widened each from private to
  package access, though the only callers then were in `Overrides` itself (after `Overrides.of` was
  added). Narrowed back by `sed`: no tool narrows a member's visibility, and `replace_declaration`
  of each would resend the whole method for one word. Sequences tried: none composes (`change_type`
  changes types, not modifiers).

## 2026-11-13: fix-batch-client-name

- No Java by hand: one `replace_text`. The golden case is resources.
