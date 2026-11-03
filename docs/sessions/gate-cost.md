# Gate cost on a large project

- **Item:** roadmap #10, "Measure gate cost on a large project; cache or
  narrow the reparse", the roadmap's Next after #9. Evidence: a 5 s
  `rename_local` on a 390-file copy of this repo (2026-11-02); in gorefactor,
  reloads that stopped recompiling the module took a 13-step batch from 546 s
  to 21 s.
- **Date:** 2026-11-03
- **Branch:** gate-cost

## Plan

The change, at the highest level: the gate's cost should grow with what an
edit touches, not with the size of the project times the number of files
parsed one by one. Today `TypeCheck.errors` parses every file separately,
each with a fresh JDT environment, before and after each edit.

Derivation, in order:

1. Measure first: an opt-in test (`GateCostTest`, run only when a system
   property names a project root) that times `TypeCheck.errors` and a
   `rename` plus `undo` on a large project. Projects: Apache Commons Math
   3.6.1 sources (990 files, 224k lines) and Commons Lang 3.17.0 (249), both
   JDK-only, unpacked from Maven Central sources jars into the scratchpad.
2. If the per-file parse dominates: one batch parse of every file
   (`ASTParser.createASTs`, one environment for the whole set), in `Jdt`,
   used by `TypeCheck.errors`. The golden cases and `SourceProjectTest` hold
   the errors it reports the same.
3. Measure again. If the before-count still costs a full parse per edit:
   keep the last after-count, keyed by each file's text, and reuse it as the
   next before-count when no file changed (a cache, checked by content, not
   trusted blindly).
4. Then the tools' own walks (`Rename`, `Signatures`, `Imports`) that parse
   every file one by one: the same batch parse, if the numbers say they
   matter.

Not in this item: narrowing the after-check to the files that depend on the
edited ones (needs a dependency graph); Gradle; multi-module builds.

## Notes

- read: `TypeCheck.errors` and `Jdt.parse` (24 lines), to see how the gate
  parses: one file at a time, each with its own environment.
- bulk: `GateCostTest` (51 lines, then 27 more) and `TypeCheckTest` (42):
  new tests. `Jdt.parseAll` and `parser` (46 lines): `parser` is the old
  setup of `parse` written out again, since `extract_method` would have cut
  it from the middle of `parse` (`setKind` and `setSource` sit between the
  options and the environment). Writing it out was easier than reordering
  `parse` first.
- refusal: `replace_declaration` of `TypeCheck.errors`: `Path` was
  ambiguous because the previous edit had removed the file's import of
  `java.nio.file.Path`. Resent with `imports` (friction log).
- plan: steps 1-4 held. Step 4 found `Signatures.Family.of` parsing all 990
  files one by one (15 s per `add_parameter`); the field, method and type
  renames' walks now share `Rename.holding` and the batch parse, which also
  folds half of #9's follow-up (one loop for "files that hold the name").
- test-after: `TypeCheckTest.aCountFollowsEveryChangeOnDisk` guards the
  cache and passed before it existed; `aCountIsReusedWhileNothingChanged`
  was red on a stub (`parses()`), then green.
- hand: downloading and unpacking the two sources jars into the scratchpad
  (`curl`, `unzip`), not the project.

## Measured

Times on Commons Math 3.6.1 (990 files, 224k lines) and Commons Lang 3.17.0
(249 files), JDK 21, this machine, one run each:

| | Lang before | Lang after | Math before | Math after |
|---|---|---|---|---|
| `TypeCheck.errors`, cold | 7.3 s | 2.8 s | 15.1 s | 4.2 s |
| `TypeCheck.errors`, again | 4.5 s | 1.1 s (parse), 12 ms (reused) | 11.3 s | 2.0 s (parse), 36 ms (reused) |
| one edit through `Gate.apply` | 7.9 s | 1.2 s | 21.8 s | 2.1 s |
| `rename` of `Precision.EPSILON` (10 files) | | | 2.7 s (batch gate only) | 2.4 s |
| `add_parameter` to `Precision.round` | | | 15.0 s (batch gate only) | 4.1 s |

`bin/session-report` (`.claude/session-log/gate-cost.jsonl`), before this
record was finished:

- tool calls: 31
- through jrefactor: 0 refactoring steps, 21 edit steps
- refused by jrefactor: 1
- by hand: 3 (the scratch download, this record, and a `grep` the report
  miscounts)
- large writes: 6 (listed in the notes)
- denied or failed (no result): 1 (an append to the friction log, still
  running when the report ran)
- reads: inspect 5

## Assessment

- **Through the tools or around them:** every Java change went through
  java-refactor-mcp; nothing was refused and then done by hand.
- **Large writes:** all new behavior or tests, except `Jdt.parser`, the old
  setup written again rather than extracted (see notes).
- **Did the plan hold?** Yes, in order: measure, batch parse, measure,
  reuse the count, measure, then the tools' walks. Narrowing to dependent
  files was not needed for a 10x gain and is left out.
- **Reading:** two short bodies, to find the cost.
- **One change that would have helped:** prefer `java.*` for an ambiguous
  name, or the import the file just lost (friction log, 2026-11-03).

## Follow-ups

- Narrow the after-check to the files that depend on the edited ones, if a
  larger project shows the single parse matters (2 s on 990 files).
- `add_parameter` parses the whole project for the family (2 s on Math)
  and then again in the gate; the family's parse could serve the gate's.
- `Imports.fix` parses the edited file two or three times per edit; small,
  not measured.
- The timing numbers are from one run each; `GateCostTest` prints, it does
  not hold a budget.
