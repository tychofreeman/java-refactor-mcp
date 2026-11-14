---
order: 228
item: 236
status: Done
---

## Item

The CLI: JSON that did not parse was an error (exit 1, "error (not a refusal)") when the object came without sections, or as an array or object `key=value`, and a refusal (exit 2) with sections (issue #491, code review)

## Status

Done 2026-11-13 (`Cli.withSections` and `Cli.typed` catch `JacksonException` and refuse with the parser's message; `CliTest.malformedJsonIsARefusalWithOrWithoutSections`)

## Evidence

Issue #491; `docs/sessions/fix-cli-malformed-json.md`
