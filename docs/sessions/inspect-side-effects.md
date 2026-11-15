# inspect: side effects in the outline

- **Item:** issue #369, last part (the owner chose option (b), following calls); roadmap #507
- **Date:** 2026-11-15
- **Branch:** inspect-side-effects

## Plan

gorefactor's `analysis.Effects.Func` in Java: each method's events in source order (its own side
effects; calls of project methods), summarized lazily and memoized, a callee's file parsed when a
summary needs it. `Inspect.bodies` takes the summaries; `facts` appends them. Goldens first: one
fixture of gorefactor's own cases (`Log`, `Twice`, `Bump`, `LogTwice`) in Java plus a call into
another file, one of the library list.

## Notes

- read: gorefactor's `internal/inspect/inspect.go` (`addEffects`, `facts`), `internal/analysis/effects.go`
  (`Func`, `call`, `summarize`, `body.visit`) and its outline test (the format).
- plan: goldens `inspect_outline_side_effects` and `..._library`, red; `inspect_outline` and
  `inspect_outline_metrics` gain the facts.
- bulk: `create_file` of `SideEffects`; one batch of four in `Inspect`.
- measured: first version (each callee file parsed whole, `parseAll`) 2-9x the outline's time on
  commons-lang. Then each callee file parsed with only the bodies wanted (`trimmed`, `wanted`), all
  of a round's files in one batch (`Jdt.parseTexts`): 2.6-3.8x. Over the owner's 2x: stopped.
- probe: temporary timing lines added and undone through the tools (`replace_text`, `undo`), and a
  variant that follows calls within the file only, built and undone, to measure option (C).
- refusal: a batch `retry` lost its `@@` sections (they are not kept for a retry); resent whole.
- refusal: `replace_declaration` of `read` named a field and a method; named by parameters.
- hand: the goldens' `result.txt` written by script from the CLI's output (resources).

## Measured

Not measured (no session-log hook). Java changes: 9 tool calls (`create_file`, five batches, three
`replace_text`), none by hand; 5 more for timing probes, undone.

Outline time on commons-lang (`Inspect.file`, warm JVM, mean of 10, ms; base = main):

| file | lines | base | with side effects |
|---|---|---|---|
| CharRange | 373 | 135 | 127 |
| math/Fraction | 632 | 31 | 19 |
| BooleanUtils | 1066 | 166 | 494 |
| text/WordUtils | 623 | 113 | 358 |
| builder/ToStringBuilder | 1215 | 130 | 337 |
| StringUtils | 7477 | 236 | 645 |
| time/DurationFormatUtils | 444 | 111 | 415 |

## Assessment

Through the tools. Not merged: the cost is over the owner's limit for files that call other
project files.

## Follow-ups

- The owner's choice: merge as is, add a per-session cache of callee summaries, or follow calls
  within the file only (options and numbers in the PR).
