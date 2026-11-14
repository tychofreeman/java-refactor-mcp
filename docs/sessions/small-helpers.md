# Small string helpers in Names, Json and Hashes

- **Item:** issue #511 (code review, group 4 of #532): simple and package names, plurals, lists,
  JSON mapper, SHA-256 copied.
- **Date:** 2026-11-14
- **Branch:** small-helpers

## Plan

`move` one copy of each into `Names` (`simpleName`, `packageOf`, `listed`; `plural` is there since
#513), `fold` each over the project, make the copies `fold` cannot match delegates and `inline`
them. New `Json.MAPPER` and `Hashes.sha256Hex` with `create_file`, the sites swapped by
`replace_text` and `replace_declaration`. Pure consolidation: check the copies are the same rule
first; where they differ (hash lengths), keep each caller's result.

## Notes

- read: `inspect` `names` of the fourteen copies at once: all the same rule but
  `MethodFamily.simpleName` (a parameter list too) and the hash lengths (8 bytes, 16, all).
- bulk: `fold` `Names.simpleName` (18), `packageOf` (5), `plural` (2), `Journal.read` (4),
  `Nodes.at` (2).
- refusal: `fold` `Names.listed` (a local before its return); delegates and `inline` instead.
- refusal: `create_file` `Json` with Jackson 2's package.

## Measured

Not measured (no session-log hook); calls are in `JREFACTOR_LOG`. Java changes in about 25 tool
calls, none by hand.

## Assessment

Through the tools. `fold` did most of it; the plural ternaries inside longer sentences are left.

## Follow-ups

- The inline plurals; `Batch`'s read-or-null of a directory.