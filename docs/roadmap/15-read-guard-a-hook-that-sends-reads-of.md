---
order: 16
item: 15
status: Done
---

## Item

Read guard: a hook that sends reads of `.java` files through `inspect`

## Status

Done (2026-11-05, the user asked for it with the write guard): `bin/read-guard`, `Guard` (`read`); Read of a `.java` file and sed, cat, head, tail, less, more, nl, awk or grep naming one are denied, files under `resources/` allowed; a `grep` that printed no text (`-c`, `-l`, `-q`) was allowed for part of 2026-11-05 (#201), then refused again once `inspect` had `pattern` (#202)

## Evidence

In gorefactor's A/B the guarded arm did worse ($2.95, 5/8 against $2.71, 6/8); its case is this repo's own sessions, where gorefactor's ended reading by shell (its cost-history 22)
