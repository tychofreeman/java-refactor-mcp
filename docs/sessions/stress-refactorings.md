# Stress the refactorings

- **Item:** the rest of roadmap order 14 (#16), "opt-in stress tests on a
  real project for tools whose rules are heuristics, with a counts-only
  mode", after the narrowed check's stress test found four misses in its
  first runs (`docs/sessions/stress-narrow-check.md`). Chosen by the user
  (2026-11-03).
- **Date:** 2026-11-03
- **Branch:** stress-refactorings

## Plan

The change, at the highest level: an opt-in test calls the refactorings
on random targets in a real project and checks the three things each
promises: it applies or refuses (never an engine error), an applied change
adds no compile error by a full count, and `undo` puts every file back.
Refusals are counted by tool and reason, which shows where the tools say no
on real code.

Derivation, in order:

1. `RefactoringStressTest`, opt-in by `-Djrefactor.stress.root`, with
   `.calls` (default 30), `.seed`, `.counts=true`. Each call: a random file
   and tool, a random target found by a syntax parse: `rename` (a declared
   name, to name + "Zq"), `move_type` (a static member type),
   `add_parameter` (a method, `int zq`, value `0`), `remove_parameter`,
   `move_parameter` (the last parameter before the first),
   `extract_method` (a run of a body's statements). Through `Tools.call`,
   as the server calls them.
2. After an applied call: the full count (`uncached`) adds nothing to the
   count before, and equals the narrowed one. After `undo`: every file's
   hash is what it was, and the count is the first one.
3. Prints, per tool: applied, refused (by the refusal's first words),
   failed; then each failure with its tool, arguments and seed. Fails on a
   failure unless counts only.
4. Run it on Commons Math and Commons Lang copies. Each failure becomes a
   red golden case, then the fix, one PR each if they are many.

## Notes

- bulk: `RefactoringStressTest` (322 lines), new.
- refusal: `create_file` of it: a leftover loop over `List.of()` in
  `restore` did not type-check. Resent with `restore` taking the project,
  so it also removes files a failed call created.
- found: Commons Lang seed 1 (40 calls, then 60), Commons Math seed 2 (60
  calls): no failures. Every call applied or refused; no applied change
  added an error by a full count; the narrowed count always matched; every
  undo put every file back. Both copies matched a fresh unpack afterwards.
- found: three refusals came from the gate, not from a tool's own check:
  the tool wrote code that does not compile, and the gate kept it out.
  These are tools' missing preconditions:
  - `move_type` `AtomicInitializer.Builder`: the nested class names
    `AbstractBuilder`, a member type that the outer class inherits from its
    supertype; `MoveType` qualifies only the outer type's own members.
  - `add_parameter` `FailableDoubleSupplier.getAsDouble()` and
    `remove_parameter` `FailableDoubleToLongFunction.applyAsLong(double)`:
    lambdas implement the method; the refusal then says "If a value or type
    does not resolve at some call, choose one that does", which does not fit.
- plan: step 4's "each failure becomes a red golden case" had no failures to
  take; the three gated refusals are follow-ups, one item each.

## Measured

| | calls | applied | refused | failed |
|---|---|---|---|---|
| Commons Lang, seed 1 | 60 | 39 | 21 | 0 |
| Commons Math, seed 2 | 60 | 38 | 22 | 0 |

Most refusals are `remove_parameter` of a parameter the body reads (a random
parameter usually is), `extract_method` of a range with `this(...)` or a
second result, and `move_parameter` swapping two arguments with side
effects: the tools' rules, as written.

`bin/session-report` (`.claude/session-log/stress-refactorings.jsonl`):

- tool calls: 12
- through jrefactor: 0 refactoring steps, 2 edit steps
- refused by jrefactor: 1
- by hand: 3
  - Bash: git checkout -q -b stress-refactorings && cat > docs/sessions/stress-refactorings.md <<'EOF'
  - Bash: cat >> docs/sessions/stress-refactorings.md <<'EOF'
  - Bash: cut -c1-600 /tmp/claude-1000/-home-cwfreeman-dev-ai-playground-java-refactor-mcp/67b6617b-7d31-4ef2-...
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 1
  - create_file src/test/java/io/github/tychofreeman/jrefactor/RefactoringStressTest.java: 322 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: O=/tmp/claude-1000/-home-cwfreeman-dev-ai-playground-java-refactor-mcp/67b6617b-7d31-4ef2-baab-a80aa...

## Assessment

- **Through the tools or around them:** all Java through java-refactor-mcp.
  By hand: the docs and this record.
- **Large writes:** the stress test, new.
- **Did the plan hold?** Steps 1-3 as written; step 4 found no failure to
  fix, and three gated refusals for follow-ups.
- **One change that would have helped:** none for this item.

## Follow-ups

- `move_type`: qualify member types and static members that the outer type
  inherits from its supertypes (`AtomicInitializer.Builder`).
- `add_parameter`, `remove_parameter`, `move_parameter`: refuse a method
  that lambdas implement, naming where, as a method reference is refused
  now.
- The stress test picks targets uniformly; `rename` of locals dominates its
  renames. Weighting toward fields, methods and types would test the
  cross-file parts more.
