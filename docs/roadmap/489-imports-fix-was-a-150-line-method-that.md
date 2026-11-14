---
order: 479
item: 489
status: Done
---

## Item

`Imports.fix` was a 150-line method that mixed finding, choosing, layout and text surgery, passed eight parameters to `toImport` (`notes` an out-parameter), and parsed `before` three times (with bindings, then twice syntax-only for its names and its imports); `Edits.write` parsed the result once more before `Edits.gated` did (issue #522, code review)

## Status

Done 2026-11-14: `Imports.plan` (a `Plan`: the edited unit, the imports to add and to remove, the choices made) and `Imports.render` (the text edits, the notes); `fix` is `render(after, plan(...))`. `toImport` and `chosen` take an `Evidence` record (`picks`, `prior`, `packages`, `notes`). `before` is parsed once, with bindings, and its names and imports read from that unit (`importsOf(CompilationUnit)`); the journal's start text is parsed only when the call or batch wrote the file before. `Edits.write` no longer parses (its callers, `create_file` and `replace_text`, are edits, which `resultParses` parses the same way). The binding parses are unchanged: the edited text, `before`, and one more per round of found imports. Measured with a scratch loop of `Imports.fix` on this project (80 calls after 10 warm-up, three interleaved runs each on a loaded 2-core machine): best minimum 97.9 ms before, 88.5 ms after; the medians (106-240 ms) are within the machine's noise. No behavior change (every golden as before); `ImportsTest`

## Evidence

Issue #522; `docs/sessions/imports-plan.md`
