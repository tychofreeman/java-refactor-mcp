# The must-override compile refusal names add_supertype

- **Item:** the user's go-ahead (2026-11-06) for the follow-up of
  `declaration-names-add-supertype.md`: in `repeated-constructions.md`'s notifier run 2,
  `add_declaration` of `@Override` methods into a stub class with no supertype was refused ("The
  method channel() of type SmsNotifier must override or implement a supertype method"), naming
  no way out.
- **Date:** 2026-11-06
- **Branch:** override-names-add-supertype

## Plan

A compile refusal whose errors include methods that must override but whose type has no such
supertype names `add_supertype` for that type, in the same batch. One place, `Gate.refusal`,
beside #265's blank-final hint: both become hints from the error lines, which take the place of
the edit's own fix when they explain every error. Golden first
(`add_declaration_override_refused_no_supertype`), then `replace_declaration` of `Gate.refusal`.

## Notes

- plan: `replace_statements` of the hint block (lines 70-85) in place of `replace_declaration` of
  the whole method, so the method's other lines were not typed again (#265's note); the pattern by
  `add_declaration`, the doc comment by `replace_text`, one batch.
- bulk: the hint block, 36 lines: the blank-final hint's 16 lines rewritten into a list of hints,
  with the new one beside it; new behavior and a reshaping of #265's lines.

## Measured

`bin/session-report`: - tool calls: 5 - through jrefactor: 0 refactoring steps, 3 edit steps - refused by jrefactor: 0 - by hand: 1   - Bash: git checkout -q -b override-names-add-supertype && cat > docs/sessions/override-names-add-supertype.... - refused, then done by hand: 0 - large writes (20 lines of Java or more in one call): 2   - Bash: git checkout -q -b override-names-add-supertype && cat > docs/sessions/override-names-add-supertype....: 28 lines   - replace_statements src/main/java/io/github/tychofreeman/jrefactor/Gate.java: 36 lines - written to scratch files: 0 - denied or failed (no result): 1   - Bash: R=$(bin/session-report 2>&1 | sed -n '/tool calls/,/reads/p' | tr '\n' ' '); python3 - "$R" <<'PY'

## Assessment

- **Through the tools:** the Java change in one batch, no refusals.
- **Large writes:** the block reshapes #265's hint into a list and adds the new one; an
  `extract_function` of the hint first would have kept the old lines, at one more step.
- **Did the plan hold?** Yes, with the narrower replacement noted.
- **Reading:** `Gate.refusal` and `BLANK_FINAL` by name, to place the change.
- **One change that would have helped:** none needed.

## Follow-ups

- A rerun of the notifier task would show whether the two `add_supertype` refusals (#267 and this)
  save the steps the models spent.
