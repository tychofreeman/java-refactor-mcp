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

Then the owner chose (B), the session's cache: `SideEffects.Read` in `Session.sideEffects`, one
batch of seven steps and `create_file` of `SideEffectsTest` (binding parses counted: 2, then 1).
Mean of 8 on commons-lang, ms (main / first / second outline / in a session after the files
above it):

| file | main | first | second | session |
|---|---|---|---|---|
| StringUtils | 198 | 770 | 220 | 757 |
| text/WordUtils | 86 | 348 | 102 | 280 |
| BooleanUtils | 89 | 290 | 98 | 306 |
| builder/ToStringBuilder | 99 | 321 | 99 | 339 |
| time/DurationFormatUtils | 108 | 524 | 120 | 203 |
| CharRange | 48 | 48 | 53 | 53 |
| math/Fraction | 28 | 21 | 20 | 23 |

## Assessment

Through the tools.

## Follow-ups

- A file's events keep the bindings they were read with until that file changes (a default to
  reopen).
