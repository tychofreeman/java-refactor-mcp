# Rename across files

- **Item:** roadmap #9, "Field, method and type rename across files;
  `rename_local` also updates Javadoc `@param`", next in order after #24.
  Evidence: 20 gorefactor entries on renames that left doc comments stale.
- **Date:** 2026-11-03
- **Branch:** rename-across-files
- **Choice:** one `rename` tool for every kind of name, replacing
  `rename_local` (the user, 2026-11-03), over adding `rename` beside
  `rename_local` (one more tool, two renames to choose from) or widening
  `rename_local` (a name that no longer says what it does).

## Plan

The change, at the highest level: a rename finds the binding named at
`file`/`line`/`name` and renames every name in the project that resolves to
it, in every file, Javadoc references included. A local's uses stay inside
its method, as now; a method takes its whole override family with it, as
the signature tools do; a top-level type takes its file with it.

Derivation, in order:

1. Green, refactor: rename the tool `rename_local` to `rename` (its `Def`,
   the handler, `GoldenTest`'s default, `ServerTest`, `SessionReport`'s list,
   the golden directories). No behavior changes; the tests stay green.
2. Red: a golden case renaming a field used in two files (`this.x`, `o.x`,
   unqualified). Today it is refused as "is a field".
3. Green: in `RenameLocal.run`, a field's occurrences are collected from every
   project file by binding key, not from one scope; the edits go through
   `Gate.applyOrRefuse` as one map of files.
4. Red, green: a method with an override in another file and calls through
   both types. The family comes from `Signatures`' family search, shared
   rather than copied; a family member that overrides a library method is
   refused as the signature tools refuse it.
5. Red, green: a top-level type used in another file (import, `new`,
   declaration types); the file moves to the new name.
6. Red, green: Javadoc. A parameter's `@param` tag, and `{@link}` /
   `@see` references to a field, method or type.
7. Refusals as cases: a field an unqualified use would lose to a local of the
   new name; a new name the type (or a supertype) already has; a type whose
   new file exists.
8. Refactor while green: `RenameLocal` becomes `Rename`, through the new type
   rename once the server serves the branch (needs a reconnect), and the
   finding of the name (`atColumn`, `byName`) is no longer locals-only.

## Notes

- plan: the user chose (2026-11-03) that a name with two meanings on the
  line (`this.count = count`) is refused, naming each with its column, and
  can be picked by column, by `name: "Counter.count"` (a member of that
  type) or by `name: "local count"`, all in `name` rather than a new `kind`
  argument. Before, the local won silently (`rename_by_name_skips_field`,
  now `rename_refused_two_kinds`).
- hand: `git mv` of the eight `rename_local_*` golden directories to
  `rename_*`, and of two of them into new cases. No tool moves a file or
  directory here (gorefactor has one for fixtures).
- refusal: a 13-step `replace_text` batch, step 9: the old text in
  `SessionReportTest` was inside a Java string, so its quotes are escaped
  there. Resent with the bare name.
- refusal: the field batch (five steps, about 15 KB), step 1 deleted
  `RenameLocal.Result` while `run` still returned it, and the import fixer
  then had two `Result` classes to choose from. Resent with `run` replaced
  first.
- refusal: same batch, step 4: `QualifiedName` is ambiguous (Eclipse
  runtime and JDT DOM). Resent with `imports`. The whole batch was sent
  three times because one step was refused each time.
- hand-adjacent: step 1 of that batch imported `javax.management.Query` for
  the `Query` record that step 4 declares. It compiled (the member type
  shadows the import), and no later step removed it. Removed with
  `replace_text`. Logged in the friction log.
- bulk: the field batch, about 330 lines of Java, mostly new behavior
  (name lookup by query, field rename, shadow check); `run` and `byName`
  (about 140 lines) were sent whole to change a few lines each.
- read: `Signatures` lines 180-330 and 630-700, to find the family search
  to share. That was reading to reuse, not to check safety.
- bulk: `Signatures.family` (59 lines) was existing code moved out of
  `Family.of` by an `add_declaration` of the copy and a `replace_text` of the
  old 50 lines. `extract_method` could not do it: the loop had three
  results (keys, declarations, library methods), which it refuses. Writing
  it out was easier than splitting the loop first.
- bulk: `renameMethod` (104 lines) and `renameType` (100 lines): new
  behavior. Each repeats the field rename's "parse every file holding the
  name, collect the names, apply, record lines" loop, shaped a little
  differently. Not folded into one helper yet (follow-up).
- refusal-free but test-after: `JournalTest.undoOfATypeRenameMovesTheFileBack`
  was written after `Gate.apply` learned to delete, so it never ran red. The
  golden `rename_type` case was the red test for the deletion.
- hand-adjacent: `add_declaration` of that test declared a stub
  `assertEquals` instead of a static import. Deleted with `replace_declaration`
  and the import added with `replace_text` (friction log).
- plan: step 8 (`RenameLocal` to `Rename`) went through the new type rename
  without a reconnect: initialize and a `tools/call` piped into the branch's
  jar (`java -jar target/java-refactor-mcp.jar .`). It moved the file and
  changed `Tools`.
- hand: the tool contract's `rename` section, by a Python script (about 44
  lines of Markdown); `replace_text` would have needed the old section (30
  lines) sent as `old`. Then a `sed` to undo the backslashes a raw string had
  kept before quotes.

## Measured

`bin/session-report` on `.claude/session-log/rename-across-files.jsonl`:

- tool calls: 71
- through jrefactor: 0 refactoring steps, 100 edit steps (the type rename
  over stdin is not in the log)
- refused by jrefactor: 3
- by hand: 10 (of which 3 are read-only `grep`s the report miscounts, 2 are
  this record, 3 are golden-directory moves, 2 are the tool contract)
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 9 (one of them is the
  Markdown script, miscounted)
- written to scratch files: 0
- denied or failed (no result): 1 (the report's own call, still running)
- reads: Bash java 4, inspect 11

Probe: Haiku renamed a field across two files from the ambiguous line, with
one refusal and one retry (5 turns, $0.0037). Tokens are not reported yet.

## Assessment

- **Through the tools or around them:** every Java change went through
  java-refactor-mcp. The hand work was file moves of golden directories (no
  tool), two doc edits by script, and this record. No refusal was followed
  by a hand edit of the same change; each was resent with a fix.
- **Large writes:** the four large ones are new behavior. Two were existing
  code sent again: `run` and `byName` whole, to change a few lines each
  (`replace_text` of those lines would have been smaller), and
  `Signatures.family`, copied out of `Family.of` because `extract_method`
  refuses three results.
- **Did the plan hold?** Yes, in order. Steps 2-7 each began with golden
  cases that failed for the stated reason. Step 8 used the new tool on this
  repo. The family search was shared, not copied (step 4). The two-meanings
  rule changed the plan (the user's choice).
- **Reading:** bodies were read to write the new behavior and to find the
  family search; none to find uses.
- **What went badly:** the same five-step batch was sent three times (about
  45 KB) because one step was refused each time. A stray import came in
  silently. A test ran green on its first run.
- **One change that would have helped:** in a batch checked at the end, fix
  imports once at the end, after every step has declared what it declares.
  That would have avoided both import refusals and the stray import
  (friction log, 2026-11-03).

## Follow-ups

- Fold the three "every file that holds the name" loops (field, method,
  type) into one walk that takes a predicate on bindings.
- Not checked: a method renamed to a name an overload already has with other
  parameter types (calls may resolve to the other one); a field renamed to a
  name a subclass field hides; a local of the new name declared in a lambda
  or anonymous class inside the method of a use.
- Comments that mention the old name in prose are not changed or listed
  (gorefactor lists them in a note).
- Type parameters and packages are not renamed.
- `bin/session-report`: Bash reads counted as writes; Markdown counted as
  Java (friction log).
