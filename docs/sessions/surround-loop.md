# `surround` with a loop object

- **Item:** roadmap #163 (order 156), the generators sketch's increment 5: `surround` on
  `replace_statements` takes `{"loop": "orders"}` beside a typed head.
- **Date:** 2026-11-13
- **Branch:** surround-loop

## Plan

The loop generator exists (`Build`, #162); the change is to hand it the statements being
surrounded as its body. `Build.around` turns the surround object into one `loop` entry whose
`body` is the replaced lines as one typed string, and `Build.statements` does the rest: the
draft, the head, the type, the indentation. In `Primitives.replaceStatements`, `surround` read
raw: a string is the typed head as before, an object goes to `Build.around`. The indentation
step `built` computed is extracted (`extract_function`) so both use it. The schema of
`surround` becomes string or object.

## Notes

- `read:` `Surround`, `Primitives.replaceStatements` and `built`, `Build`'s `walk`, `local`,
  `entries`, `generator` through `inspect` ranges.
- `plan:` the derivation found a gap the plan did not: the variable a loop picks was free of
  the scope around the loop, not of the body's own locals, which `surround` makes common (the
  body is existing code). `walk` now also skips names the body declares (`Build.declares`);
  golden `replace_statements_surround_loop_body_declares`.
- `hand:` the four golden cases (resource files), with the shell, as the brief allows.
- Through the tools: `extract_function` (`Primitives.step`), then one batch of seven steps
  (two `add_declaration`, five `replace_text`), check `"end"`.

## Measured

Not measured: the session-log hook is not loaded in this session.

## Assessment

Through the tools; no large write. The plan held; the one addition came from writing the
goldens first. No tool got in the way.

## Follow-ups

- None for this item. The guard (#164) is next.
