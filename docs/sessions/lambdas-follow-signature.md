# Lambdas follow a signature change

- **Item:** roadmap #57 (local session's list, 2026-11-03), the follow-up
  of `docs/sessions/refuse-lambda-implemented.md`: the signature tools
  refuse a method a lambda implements; they could change the lambdas too.
  The user asked (2026-11-04) to go ahead with the list without asking, taking
  the obvious choice.
- **Date:** 2026-11-04
- **Branch:** lambdas-follow-signature

## Plan

The change, at the highest level: a lambda that implements the method is
one more declaration of it, whose parameter list the change rewrites:
`add_parameter` adds the name (with the type when the lambda's parameters
are typed), `remove_parameter` drops it unless the lambda's body reads it
(refused, as a method body that reads it is), `move_parameter` reorders.

1. Red: golden `sig_lambda_add`, `sig_lambda_remove`, `sig_lambda_move`,
   `sig_lambda_elsewhere` (the four refusal cases of #72, now with `out/`),
   and `sig_lambda_remove_refused_reads`. The old `sig_refused_lambda_*`
   directories go (`git rm`: no tool removes a directory).
2. Green: `MethodFamily` collects `Signatures.Lambda` (source and node)
   instead of refusing; `SignatureChange.lambda` rewrites a lambda's
   parameters from the start of the lambda to its arrow; the three tools
   give it the new list.
3. The tools' descriptions and the contract.

The obvious choices taken: the new lambda parameter is named as the
method's; parentheses always (`(a) -> a` after a removal).

## Notes

- bulk: the golden cases (five, 27 files), new; one input of mine did not
  compile (a one-parameter lambda for a two-parameter interface), fixed
  before the red run.
- test: red as planned (four refused, one with the old message).
- refusal: the code batch, step 2: `old` two spaces short, read off
  `inspect` again. Resent from `cat -A`.
- hand: `git rm -r` of the four `sig_refused_lambda_*` cases: no tool removes
  a directory.
- found: Commons Lang stress seed 1: no failures, no refusal by the gate;
  the `Failable*` methods lambdas implement now change and undo cleanly.

## Measured

- tool calls: 19
- through jrefactor: 0 refactoring steps, 43 edit steps
- refused by jrefactor: 1
- by hand: 1
  - Bash: cat > docs/sessions/lambdas-follow-signature.md <<'EOF'
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 1
  - replace_text src/main/java/io/github/tychofreeman/jrefactor/Signatures.java: 26 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: python3 - <<'EOF'
- reads: Bash java 5, inspect 1

## Assessment

- **Through the tools or around them:** all Java and fixtures through
  java-refactor-mcp; by hand the directory removal (no tool) and the docs.
- **Did the plan hold?** Yes.

## Follow-ups

- A new lambda parameter whose name clashes with a local around the lambda
  is refused by the gate (compile error), not renamed.
