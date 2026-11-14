---
order: 34
item: 41
status: Done
---

## Item

`Extraction.apply` is about 160 lines (classify, decide return, build signature, compose text, compute lines); `Selection.body` Javadoc says "innermost" but takes the outermost body

## Status

Done

## Evidence

Full code review 2026-11-03: reading, not run. Split into `Plan` (refusals and classification), `callOf` (the call) and `methodText` (the render); `apply` is about 50 lines. The Javadoc now says outermost. Record: `docs/sessions/extraction-apply-split.md`
