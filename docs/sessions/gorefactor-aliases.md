# gorefactor's tool names as aliases

- **Item:** issue #372, roadmap #203
- **Date:** 2026-11-15
- **Branch:** gorefactor-aliases

## Plan

One alias `Def` per name, its arguments gorefactor's (from each tool's `args` struct in
go-refactor-mcp), its handler mapping them and calling the tool here through `Tools.call`.
Fixtures reused from the target tools' goldens, so each alias case must give the same output.

## Notes

- read: gorefactor's `introparam.go`, `paramobject.go`, `deadcode.go`, `prepare.go`,
  `parameterize.go` (argument structs and descriptions).
- found: gorefactor's `parameterize` merges functions that differ only in constants; the issue's
  table mapped it to `extract_variable` with `as=parameter`. Not built; the owner chooses.
- plan: seven goldens `alias_*`, red; the expected output of `convert_params_to_object` made by
  running `extract_type` with every parameter through the CLI on a copy.
- bulk: `create_file` of `GoNames.java`; one batch of two `replace_text` in `Tools`.
- hand: none in Java.

## Measured

Not measured (no session-log hook). Java changes: 3 tool calls, none by hand.

## Assessment

Through the tools.

## Follow-ups

- `parameterize`: the owner's choice (alias of nothing here, or a tool that merges functions
  differing in constants, as `fold` then `inline` partly does).
