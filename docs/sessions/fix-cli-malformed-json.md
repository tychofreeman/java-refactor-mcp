# CLI: JSON that does not parse is a refusal

- **Item:** issue #491 (code review of 2026-11-13), roadmap #236
- **Date:** 2026-11-13
- **Branch:** fix-cli-malformed-json

## Plan

Catch `JacksonException` where the CLI parses JSON without a catch (`withSections` without
sections, `typed` for an array or object `key=value`) and refuse with the parser's message, as
the path with sections does. A `CliTest` first.

## Notes

- plan: the test was red as the issue said ("error (not a refusal): ...EndOfInputException").
- plan: `typed`'s array and object values were the same bug at a second site, fixed with it.
- plan: branched after #559 (the CLI's run log), which changed `Cli`.

## Measured

Not measured: the session-log hook is not loaded here (from #559, `JREFACTOR_LOG` logs these
runs). Four calls; no Java by hand.

## Assessment

Through the tools.

## Follow-ups

None.
