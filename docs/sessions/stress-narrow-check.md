# Stress the narrowed check

- **Item:** the follow-up of `docs/sessions/narrow-check.md` (#56), and
  roadmap order 14 (#16), "opt-in stress tests on a real project for tools
  whose rules are heuristics, with a counts-only mode". The narrowing
  rests on a rule ("a header change reaches another file only through a
  name it writes") that only examples have checked so far. Chosen by the
  user (2026-11-03).
- **Date:** 2026-11-03
- **Branch:** stress-narrow-check

## Plan

The change, at the highest level: an opt-in test makes many random edits
to a real project's declarations, of the kinds that change what other files
see, and after each edit and each restore compares the narrowed count with
a full one. A difference is a case the rule misses.

Derivation, in order:

1. `NarrowCheckStressTest`, opt-in by `-Djrefactor.stress.root=<dir>`, with
   `jrefactor.stress.edits` (default 30) and `jrefactor.stress.seed`
   (printed, for a rerun). Each edit: a random file, a random declaration in
   it, one of: rename a method or field or type (its declaration only), add
   a parameter, make a method private or static, change a method's return
   type or a field's type, drop a type's supertypes, delete a method, and a
   body-only change as a control. Located by a syntax parse, applied as
   text, written, compared (`TypeCheck.errors` against `uncached`), then
   restored and compared again.
2. Prints the edits made, the files each re-checked, the mismatches with
   the edit and the keys that differ; fails on a mismatch unless
   `jrefactor.stress.counts=true` (counts only).
3. Run it on Commons Math and Commons Lang. A mismatch is the next red
   test: a `TypeCheckTest` case of it, then the fix in `Headers` or
   `TypeCheck`.

## Notes

- bulk: `NarrowCheckStressTest` (216 lines), new: eleven kinds of edit and
  the comparison.
- found: Commons Lang, 40 edits, seed 611179456014065: 0 misses, 21.5 files
  re-checked per edit. Commons Math, 60 edits, seed 611243187649090: 3
  misses, all through inheritance. `AbstractRegion.isFull` made static broke
  subclasses that name `AbstractRegion`, not `isFull`. Renaming
  `FieldMatrixPreservingVisitor` broke `MatrixUtils`, whose types extend
  `DefaultFieldMatrixPreservingVisitor` and never name it. Dropping
  `NumberIsTooSmallException`'s supertypes broke `FunctionUtils`, which
  throws a subclass of it.
- plan: as step 3 said, two of these became red `TypeCheckTest` cases
  (a member a subclass inherits; a supertype seen through a subclass), then
  the fix. `Headers` keys members by their type (`Shop#total`) and keeps the
  type names each header mentions. The narrowing follows them: a changed
  type or member makes its type "changed", and so does any type whose
  header names a changed type (a subtype), and any member whose header does
  (a method taking it, for a lambda). The files of those types are
  re-checked, plus files that mention any of their names or write `new T`.
- bulk: that batch (12 steps, about 150 lines across `Headers` and
  `TypeCheck`): new behavior, with `narrowed` sent whole since most of it
  changed.
- found: rerun, seeds 611243187649090 and 2: 1 miss each, of one kind.
  Renaming a visitor type changes `FieldMatrix#walkInColumnOrder`'s header,
  and `SparseFieldMatrix`, a subtype of `FieldMatrix`, was not re-checked.
  Fix: a member whose header names a changed type makes its own type
  changed too.
- refusal: that fix's `replace_text` at first: spotless had re-indented
  the lines, so my `old` was stale. The stress runs that followed ran on
  the old rule. I stopped them and read the lines again.
- hand: stopping a stress run left one Commons Math file mid-edit
  (`FieldStepInterpolator.java`). I found it with a `diff -r` against a
  fresh unpack of the sources jar, and restored it with `cp`. Run the
  stress test on a copy.
- found: after that fix, Commons Math seeds 611243187649090, 2, 3, 4 (60
  edits each): 0 misses, 193-259 files re-checked per edit; Commons Lang
  seed 5 (60 edits): 0 misses, 48.5 files per edit. The copy matched a
  fresh unpack afterwards.

## Measured

| Commons Math (990 files) | before #56 | #56 (names only, unsound) | now |
|---|---|---|---|
| one edit through `Gate.apply` (a comment) | 2119 ms | 63 ms | 64 ms |
| `rename` of `Precision.EPSILON` | 2410 ms | 894 ms (34 files) | 1324 ms (114 files) |
| `add_parameter` to `Precision.round` | 3156 ms | 1406 ms (16 files) | 1914 ms (124 files) |

Stress, 0 misses after the fixes: 240 edits on Commons Math, 100 on
Commons Lang (one run of 40 before them too).

`bin/session-report` (`.claude/session-log/stress-narrow-check.jsonl`):

- tool calls: 31
- through jrefactor: 0 refactoring steps, 15 edit steps
- refused by jrefactor: 2
- by hand: 4
  - Bash: git checkout -q -b stress-narrow-check && cat > docs/sessions/stress-narrow-check.md <<'EOF'
  - Bash: S=/tmp/claude-1000/-home-cwfreeman-dev-ai-playground-java-refactor-mcp/67b6617b-7d31-4ef2-baab-a80aa...
  - Bash: S=/tmp/claude-1000/-home-cwfreeman-dev-ai-playground-java-refactor-mcp/67b6617b-7d31-4ef2-baab-a80aa...
  - Bash: cat >> docs/sessions/stress-narrow-check.md <<'EOF'
- refused, then done by hand: 1
  - replace_text (refused: old is not in src/main/java/io/github/tychofreeman/jrefactor/TypeCheck.java. Copy it from the file exactly (from inspect, without the line numbers).), then Bash: S=/tmp/claude-1000/-home-cwfreeman-dev-ai-playground-java-refactor-mcp/67b6617b-7d31-4ef2-baab-a80aa...
- large writes (20 lines of Java or more in one call): 5
  - create_file src/test/java/io/github/tychofreeman/jrefactor/NarrowCheckStressTest.java: 216 lines
  - add_declaration src/test/java/io/github/tychofreeman/jrefactor/TypeCheckTest.java: 25 lines
  - replace_declaration src/main/java/io/github/tychofreeman/jrefactor/Headers.java: 35 lines
  - replace_declaration src/main/java/io/github/tychofreeman/jrefactor/Headers.java: 27 lines
  - replace_declaration src/main/java/io/github/tychofreeman/jrefactor/TypeCheck.java: 77 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: R=$(bin/session-report 2>/dev/null | tail -30); cat >> docs/sessions/stress-narrow-check.md <<EOF
- reads: Bash java 1, inspect 3

## Assessment

- **Through the tools or around them:** all Java through java-refactor-mcp.
  By hand: restoring one file of the Commons Math copy after I stopped a
  run, the timing script, the docs and this record.
- **Large writes:** the stress test, then the rule's new data and
  closure: new.
- **Did the plan hold?** Yes; step 3 happened twice, as planned: each miss
  became a red case or a stress rerun, then a fix.
- **What went badly:** #56 shipped a rule that missed edits seen through
  inheritance; its own tests and the golden cases had no subclass in
  another file. It was merged for about an hour before this caught it.
  The stress run I stopped left a file changed in the copy.
- **One change that would have helped:** the stress test before #56 was
  merged, not after. Noted in the session notes as how to check a
  heuristic in the gate.

## Follow-ups

- The stress test's edits are declaration edits; body-only edits are one
  kind of eleven. Edits to imports and package lines are not made.
- A restore that survives a killed run: write the original to a side file
  first, and restore it at the next start.
- The narrowing is wider now (about a fifth of Math's files for a header
  change). Splitting `types` by what reaches what (a subtype versus a
  method's parameter type) could narrow it again, with the stress test to
  hold it.
