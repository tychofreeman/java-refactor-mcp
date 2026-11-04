# extract_method refuses assigning a final field

- **Item:** roadmap #66, found by `RefactoringStressTest` on Commons Math
  (seed 7, `docs/sessions/stress-weighted-targets.md`): extracting a line
  of `Interval`'s constructor that assigns the blank final `upper` was
  refused only by the gate ("may not have been initialized"). Chosen by the
  user (2026-11-04).
- **Date:** 2026-11-04
- **Branch:** extract-blank-final

## Plan

The change, at the highest level: a range that assigns a final field
(possible in code that compiles only for a blank final, in a constructor or
initializer) cannot move into a method, which may not assign it; refuse it
up front, naming the line and the field, as the range with `this(...)` is
refused.

1. Red: golden `extract_method_refused_final_field`: `this.upper = upper;`
   in a constructor, refused with "line 9 assigns the final field upper".
2. Green: `Extraction.refuseFinalFields`, beside
   `refuseConstructorCalls`: an assignment, `++` or `--` in the statements
   whose target is a final field.

## Notes

- test: red as planned (refused by the gate, not by the tool).
- plan: steps 1-2 as written; the description and the contract list the
  refusal.
- found: Commons Math stress seed 7: refused up front ("line 46 assigns the
  final field upper"); no refusal by the gate, no failures; the copy clean.
  Seed 7 is now clear of gated refusals.

## Measured

- tool calls: 12
- through jrefactor: 0 refactoring steps, 7 edit steps
- refused by jrefactor: 0
- by hand: 1
  - Bash: cat > docs/sessions/extract-blank-final.md <<'EOF'
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 1
  - add_declaration src/main/java/io/github/tychofreeman/jrefactor/Extraction.java: 47 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: python3 - <<'EOF'
- reads: Bash java 3

## Assessment

- **Through the tools or around them:** all through java-refactor-mcp.
- **Did the plan hold?** Yes.

## Follow-ups

- None.
