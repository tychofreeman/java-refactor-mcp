# Stress targets weighted by kind

- **Item:** roadmap #60 (local session's list, 2026-11-03): the stress
  test picked `rename` targets uniformly among declared names, so locals
  and parameters, the most numerous, crowded out fields, methods and types,
  the renames that reach other files.
- **Date:** 2026-11-04
- **Branch:** stress-weighted-targets

## Plan

The change, at the highest level: a `rename` target is drawn by kind first
(type, method, field, local), then a name of that kind.

1. `RefactoringStressTest`: group declared names by `kind`, pick a kind
   uniformly among those the file has, then a name.
2. Run it on Commons Math; failures, if any, are the next items.

## Notes

- test-only change; no red step (it changes what the test draws, not what
  the code does).
- found: Commons Math seed 7, 60 calls: no failures; rename applied 19
  (fields, methods and types among them), the copy clean after. Two
  refusals by the gate, new items: #65 `move_type` of
  `BSPTreeVisitor.Order` (`OutlineExtractor`, another package, implements
  `BSPTreeVisitor` and writes `Order` unqualified), and #66
  `extract_method` of a line assigning `Interval`'s blank final `upper`.

## Measured

- tool calls: 6
- through jrefactor: 0 refactoring steps, 2 edit steps
- refused by jrefactor: 0
- by hand: 1
  - Bash: cat > docs/sessions/stress-weighted-targets.md <<'EOF'
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 0
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: python3 - <<'EOF'

## Assessment

- Test-only; the weighting found two gaps in its first run.

## Follow-ups

- #65 and #66 (roadmap).
