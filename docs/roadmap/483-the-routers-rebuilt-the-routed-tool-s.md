---
order: 473
item: 483
status: Done
---

## Item

The routers (`move`, `inline`) rebuilt the routed tool's arguments by hand, so an argument added to `move_member` or `inline_variable` would be dropped unless the router copied it too, and forwarded `preserve_exceptions`, which `Tools.call` had already set on the session; the `create_*` aliases mapped each argument to itself (issue #524, code review)

## Status

Done 2026-11-14: `Args.routed(tool, takes)` hands on every given argument the target takes, under the target's name (refusals still name `move_file`, `move_member`, ...), and the routers keep only their refusals and their choice of target; `insertOne` passes every argument but the placement ones (`PLACEMENT`) into the build entry, renamed only where the names differ (`over` to `loop`). No behavior change (every golden as before); `ArgsTest`. Left open: the ~25 adapters from `Args` to positional parameters stay, as moving each tool to take `Args` touches classes other open items rewrite

## Evidence

Issue #524; `docs/sessions/tools-routers.md`
