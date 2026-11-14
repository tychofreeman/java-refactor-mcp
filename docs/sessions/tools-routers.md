# Routers forward arguments by the target's names

- **Item:** issue #524 (code review core #15, group 5 of #532): `move` and `inline` rebuild the
  routed tool's arguments by hand, so an argument added to a target is dropped unless the router
  also copies it; the `create_*` aliases map each key to itself.
- **Date:** 2026-11-14
- **Branch:** tools-routers

## Plan

Routing becomes "these arguments, only those the target takes, under the target's name": one
`Args` method, so the router keeps only its refusals and its choice of target. The forwarding of
`preserve_exceptions` goes (`Tools.call` has set the session's flag from the outer arguments; the
routed handler runs inside that call). `insertOne` builds its entry from every argument but the
placement ones, renamed by a map that names only real renames (`over` to `loop`). Operations: add
the `Args` method (test first), replace `move`, `inline` and `insertOne` with `replace_declaration`,
shrink the alias maps with `replace_text`. The ~25 adapters from `Args` to positional parameters
stay: moving each tool to take `Args` touches 25 classes other agents are editing.

## Notes

- plan: no behavior change, so no failing golden first; `ArgsTest` was written first (`create_file`,
  which declared the stubs `Args.routed` and `Args.values`) and failed on them.
- plan: `routed` drops arguments sent as null, as the routers did (they copied only values they
  read as given): `move` with `"package": null` still moves the type in its package.
- tools: `create_file` (ArgsTest), a batch of two `add_declaration`s (the stubs replaced), a batch
  of two `replace_declaration`s (`routed` and its test), a batch of seven steps for `Tools`
  (`replace_declaration` of `move`, `inline`, `insertOne`, `add_declaration` of `PLACEMENT`,
  three `replace_text`s for the alias maps). No Java by hand; Spotless reformatted the lambdas.

## Measured

Not measured: the session-log hook is not loaded here. 6 tool calls (one `create_file`, three
batches, two reads through `inspect`), 0 hand edits of Java.

## Assessment

Through the tools. The plan held. The issue's other suggestion, tools that take `Args` in place
of the ~25 adapters, was left: each touches a tool class, and several are being rewritten by other
open items.

## Follow-ups

- The adapters from `Args` to positional parameters (`Tools.extractVariable`, `moveStatement`, ...).
