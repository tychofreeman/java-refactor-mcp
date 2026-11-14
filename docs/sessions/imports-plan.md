# Imports.fix split into plan and render

- **Item:** issue #522 (code review core #10, group 5 of #532): `Imports.fix` is a 150-line
  method that parses the same text several times.
- **Date:** 2026-11-14
- **Branch:** imports-plan

## Plan

Measure first (a timed loop of `Imports.fix` on this project as the cost root). Then: `plan`
(lines finding and choosing, returning a `Plan` record) and `render` (layout and text edits), each
added whole with `add_declaration`, and `fix` replaced by `render(after, plan(...))`; an `Evidence`
record in place of `toImport`'s four last parameters; `before` read from its one binding parse;
`Edits.write`'s parse dropped, since `Edits.gated` parses the same text first under an edit. No
behavior change, so no failing golden; `ImportsTest` for `plan` and `render`.

## Notes

- read: `Jdt.parse` and `Jdt.parseSyntax` set the same compiler options, so names and imports read
  from the binding parse of `before` are those of the syntax parse.
- read: `Edits.write`'s only callers are `create_file` and `replace_text`, both `Kind.EDIT`, so
  `resultParses` takes the edit branch, the same `parses(text, "the result")`.
- refusal: the first batch replaced `importsOf(String)` with a unit overload, but
  `packageImports` still called it with a string; retried the step as an added overload, the
  string one delegating (`batch` `retry`).
- tools: one batch of thirteen steps (`replace_declaration` of `fix`, `toImport`; `add_declaration`
  of `Plan`, `Evidence`, `plan`, `render`, `importsOf(CompilationUnit)`; `replace_text`s for
  `chosen` and `Edits.write`), one batch of two `replace_text`s, `create_file` for `ImportsTest`.
  One `replace_text` step sent the same old and new text (a no-op in the batch). No Java by hand.
- measure: a scratch test (not committed) ran `Imports.fix` 80 times after 10 warm-up on
  `Lines.java` of a copy of this project, with an edit that needs two imports and drops one; three
  runs before and after, interleaved. Minimums: before 131.6, 97.9, 98.5 ms; after 88.5, 108.8,
  93.6 ms. Medians 106-240 ms either way: the machine (load 3-4.5 on two cores, five agents) is
  noisier than the change. `GateCostTest` `timesACall` of the same edit as `replace_text`: 1.6-2.9 s
  before, so the gate, not the import fix, is most of a call.

## Measured

Not measured by the session log (the hook is not loaded here). 0 hand edits of Java.

## Assessment

Through the tools. The plan held. The saving is syntax parses only (two or three of `before` a
call, one of the result in `create_file` and `replace_text`); the binding parses, the cost that
matters, stay: the edited text, `before`, and one per round of the lambda loop.

## Follow-ups

- The loop's binding parse per round could reuse the gate's parse of the fixed text, if the gate
  took the unit.
- `FromFields.complete` parses the fixed text again (not touched here).
