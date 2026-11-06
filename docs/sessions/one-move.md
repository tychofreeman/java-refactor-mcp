# One move

- **Item:** roadmap #121, gorefactor's `docs/parity.md` section 2 (the user, 2026-11-05: one
  `move`, "easier for the model to reach for", after Go and TypeScript build theirs; they did,
  gorefactor #565).
- **Date:** 2026-11-06
- **Branch:** one-move

## Plan

(Written after the code, by mistake: the record was missed at the start of this item; the plan
below is the one followed.)

`move` is published in place of `move_type`, `move_member` and `move_file` and routes to them by
its arguments, as gorefactor's `Router`: no `name` moves the file; `name` with `package` the
file's type; `name` with `to` a member; `name` alone a nested type. The three stay names `batch`
and the CLI accept (`Tools.ALIASES`), so recipes and old logs work. Refusals that named the old
tools name `move`, since a model calls only published tools.

gorefactor merged its `move` before the A/B check of the published list, which stays a follow-up
for the user to decide; this does the same.

## Notes

- read: the `MOVE_FILE` `Def`; gorefactor's `refactor/move.go` (the routes and their refusals).
- One batch (the `Def`, the router, `ALL`, `ALIASES`); every existing test passed; then the
  refusal texts (12 in `MoveMember`, one each in `MoveFile`, `MoveType`, `MoveToPackage`, `Guard`,
  `Fold`, a description) by one batch; two goldens' fragments followed.
- Goldens: one per route, copied from the old tools' with `tool=move`, and a refusal.
- The #106 shift took one `old` two spaces too deep (`Guard`).
- refusal: appending to this record before it existed ("Create it with create_file").

## Measured

Through jrefactor: 3 edit batches and the docs; one refusal (the missing record); by hand the
goldens (resources, by `cp` and `sed`) and this record.

## Assessment

Through the tools. The record was not started first, as the skill asks: a process slip, noted.

## Follow-ups

- The A/B check of the published list (ask the user).
- #122: one `inline`.
