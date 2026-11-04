# Narrow the check after an edit

- **Item:** the follow-up of #10 (`docs/sessions/gate-cost.md`) and of
  `docs/sessions/cli-cache.md`: what is left of an edit's cost is the type
  check after it, which parses every file. Chosen by the user (2026-11-03).
- **Date:** 2026-11-03
- **Branch:** narrow-check

## Plan

Measured first, Commons Math 3.6.1 (990 files), `GateCostTest`: one edit
through `Gate.apply` 2119 ms; `rename` of `Precision.EPSILON` (10 files)
2410 ms; `add_parameter` to `Precision.round(double, int)` 3156 ms. Each
parses the whole project once after the edit (the count before is reused).

The change, at the highest level: after an edit, only the files whose
errors can have changed are type-checked again: the changed files, and the
files that mention a name whose declaration the change touched. The counts
of the other files are kept from the last count.

What other files can see of a file is its declarations' headers: a type's
header (modifiers, name, type parameters, supertypes, record components),
a method's header (everything but the body), a field or enum constant
whole, each with the package and the imports its text names. A change that
leaves every header alone (a body, a comment, an import only a body uses)
can change errors only in its own file. A header change can reach a file
only through its name: a file that calls `b.a().m()` names `m`, though
not `A`.

Derivation, in order:

1. Red: `TypeCheckTest`: a body change in `A` re-checks only `A`; a
   renamed method in `A` re-checks `A`, `B` (which calls it) and `D` (which
   reaches it as `b.a().m()`), not `C`, and counts their new errors; a new
   file re-checks the file that named its missing type. `TypeCheck.lastParsed`,
   the files the last count parsed, is a stub until step 3.
2. Green: `Headers.of(text, unit)`: for each simple name a file declares
   (types, members, nested types, not inside bodies), a hash of the headers
   that declare it.
3. Green: `TypeCheck.Counted` keeps each file's headers; `errors` with a
   last count: the files whose hash changed (added and removed included),
   the names whose headers changed, the files that mention one as a word;
   counts those files and keeps the rest.
4. The guard: `GoldenTest` already compares each step's count with a fresh
   one (`uncached`). Also on Commons Math: after each timed call,
   `GateCostTest` compares the narrowed count with a fresh one.
5. `TypeCheck.save`/`load` keep the headers too; an old cache file is
   ignored.
6. Measure again.

## Notes

- refusal: `add_declaration` of the tests: the stub for `TypeCheck.lastParsed`,
  called in an enhanced `for`, was typed so the loop could not iterate it.
  Resent with the call assigned to a `List<Path>` first (friction log).
- bulk: the tests (59 lines), new.
- bulk: `Headers` (157 lines), new behavior.
- refusal: the `TypeCheck` batch, step 8 (`Session`): `Path` ambiguous
  (three classes), as in #10. Resent with `imports`.
- bulk: the `TypeCheck` batch (about 120 lines: `narrowed`, the new
  `errors`, `count` with headers, save and load): new behavior, with
  `errors`' old lines sent back as `old` to change them.
- test: all tests, `GoldenTest`'s fresh-count comparisons included, passed
  on the first run of the change.
- hand-adjacent: my first `GateCostTest` edit left two meaningless lines (a
  temp-file write and an assertion that is always true). Replaced by the
  intended assertion; two `replace_text` calls, one refused at first
  because the other had not removed what it depended on.
- plan: steps 1-6 held.

## Measured

Commons Math 3.6.1 (990 files), `GateCostTest`, one run each; the narrowed
count equalled a fresh one after each call and each undo:

| | before | after | files re-checked |
|---|---|---|---|
| one edit through `Gate.apply` (a comment) | 2119 ms | 63 ms | 1 |
| `rename` of `Precision.EPSILON` (10 files) | 2410 ms | 894 ms | 34 |
| `add_parameter` to `Precision.round(double, int)` | 3156 ms | 1406 ms | 16 |

This repo, `bin/jrefactor rename` of a local, warm cache: 2.35 s to 1.24 s.

`bin/session-report` (`.claude/session-log/narrow-check.jsonl`):

- tool calls: 24
- through jrefactor: 0 refactoring steps, 13 edit steps
- refused by jrefactor: 3
- by hand: 4
  - Bash: export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 S=/tmp/claude-1000/-home-cwfreeman-dev-ai-playgr...
  - Bash: cat > docs/sessions/narrow-check.md <<'EOF'
  - Bash: cat >> docs/sessions/narrow-check.md <<'EOF'
  - Bash: export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 && ./mvnw -q spotless:apply && ./mvnw verify 2>&...
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 4
  - add_declaration src/test/java/io/github/tychofreeman/jrefactor/TypeCheckTest.java: 59 lines
  - create_file src/main/java/io/github/tychofreeman/jrefactor/Headers.java: 157 lines
  - replace_text src/main/java/io/github/tychofreeman/jrefactor/TypeCheck.java: 69 lines
  - replace_text src/main/java/io/github/tychofreeman/jrefactor/TypeCheck.java: 23 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: python3 - <<'EOF'
- reads: inspect 3

## Assessment

- **Through the tools or around them:** all Java through java-refactor-mcp.
  By hand: the docs, this record, the timing script in the scratchpad.
- **Large writes:** `Headers` and `TypeCheck`'s narrowing, new.
- **Did the plan hold?** Yes, in order; the measurement before showed the
  check after an edit was the whole cost left.
- **Reading:** `TypeCheck` and `GateCostTest`, to change them.
- **One change that would have helped:** a stub typed from an enhanced
  `for` (friction log, 2026-11-03), and preferring `java.nio.file.Path`
  (friction log, #10).

## Follow-ups

- What still costs time in a rename is its own walk: the files holding the
  name, parsed together (`Rename.holding`).
- Not sound for: a header whose effect on another file goes through no name
  that file writes (none found yet; e.g. a change to a type's members seen
  through `var` and a method reference). The comparisons above are the
  guard; an opt-in run over many random edits on Commons Math would be a
  stronger one.
