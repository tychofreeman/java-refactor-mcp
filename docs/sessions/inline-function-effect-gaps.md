# inline_function: effects before a use, and deferred uses (roadmap #67, the gaps)

- **Item:** roadmap #67's gaps. The other session fixed #67 on `main`
  (`a5c8b94`, `docs/sessions/effect-order.md`) while this session's PR #118
  fixed it another way. Run against `main`, #118's goldens showed two cases
  that `main` does not cover: an effect of the body that runs before an
  argument's use, and a use inside a lambda, anonymous class or branch. The
  user chose (2026-11-04) to keep `main`'s version and add only those, over
  merging #118 onto it (casts and qualification in place of `main`'s
  refusals) or keeping `main`'s as is. #118 was closed.
- **Date:** 2026-11-04
- **Branch:** inline-function-effect-gaps

## Plan

The change: an argument that is not *fixed* (it calls, assigns, or reads a
field or array element) is refused when the inlined body would evaluate it
at another time than the call did:

- an effect of the body (call, creation, assignment, increment; not inside
  a lambda or anonymous class) ends before the argument's use: originally
  the argument ran first;
- the use is inside a lambda or anonymous class (it would run later, or
  read later state), or, for an argument with an effect, in a `?:` branch
  or to the right of `&&` / `||` (it might not run).

Derivation:

1. Red: goldens `inline_function_refused_effect_before` (a field read after
   `Counter.bump()`, the body's call in another class, so `main`'s member
   rule does not refuse it first), `inline_function_refused_in_lambda` and
   `inline_function_refused_in_branch`, by `create_file`.
2. Green: `add_declaration` of `checkTiming` and its helpers (`fixed`,
   `effectsIn`, `deferral`, `changes`), from #118's versions; a
   `replace_text` adding its call after `checkArguments(args, params,
   useCount, name)` in `replacementFor`.

## Notes

- `read:` `a5c8b94`'s diff of `InlineFunction`, to see what `main` already
  refuses; #118's helpers were reused, not reread.
- `bulk:` `checkTiming` and four helpers, about 150 lines by
  `add_declaration`: copied from closed PR #118 (written there by
  `add_declaration` too). No tool moves code between branches; pasting it
  was the way.
- Three goldens red first (`main` applied all three), green after.

## Measured

From `bin/session-report`:

- tool calls: 10 (to the report; then the docs)
- through jrefactor: 17 edit steps
- refused by jrefactor: 0
- by hand: 1 (the record), then the docs by `sed`, a heredoc and Python
- large writes: 1 (the helpers)
- reads: Bash java 1

## Assessment

- **Through the tools or around them:** through, for the Java and the
  description; the docs by shell.
- **Large writes:** existing code from another branch, typed again through
  `add_declaration`. A cherry-pick would not apply: #118's helpers sat
  among other changes in the same file.
- **Did the plan hold?** Yes.
- **Reading:** only the other session's diff.
- **One change that would have helped:** checking `main` and open branches
  before starting #67: the duplicate work cost a PR. Recorded in the session
  notes as a step before fixing a planned row.

## Follow-ups

- An argument with an effect and another that reads a field, in another
  order in the body than in the call: `main` refuses only two arguments
  with effects. Not added (outside the two gaps chosen).
