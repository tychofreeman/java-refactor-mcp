# A batch step's arguments beside `tool`

- **Item:** the refusal for a `batch` step that puts its arguments beside `tool` rather than
  under `args` (the owner's follow-up of four friction-log entries, 2026-11-10 and 2026-11-13);
  roadmap #196
- **Date:** 2026-11-13
- **Branch:** batch-args-beside-tool

## Plan

One check in `Batch.run`, before any step runs: for each step whose tool is known, the keys
beside `tool` other than the step's own (`tool`, `args`, `id`) that the tool takes as arguments.
If any, refuse naming them and showing the first such step with them under `args`, then list the
other such steps by number. Inside the existing try, so the refused batch is kept for `retry`.
Test first: golden `batch_refused_args_beside_tool` (`refused.txt`), three steps, the first right,
the second and third with their arguments beside `tool`.

## Notes

- read: `Batch` outline, `run` and `steps`, `retried` through `inspect`; `Tools.named`, `Def`,
  `ARG_ALIASES`, `RETIRED_ARGS`, `call` through `inspect` with `names`.
- refusal: `inspect` with `names` and `file` together ("leave out file"); resent without `file`.
- refusal: a batch whose JSON was an argument and whose `@@` sections came on stdin; the
  sections go after the JSON in the same input, so sent the whole as `-` from a heredoc.
- plan: the golden case failed with today's "replace_text needs file".
- `STEP_KEYS`, `argumentsUnderArgs`, `takes`, `listed` and the call in `run`: one `batch`
  (two `add_declaration`, one `replace_text`), check "end". No hand edits to Java.
- Golden case, docs: ordinary file tools (resources and Markdown).

## Measured

Not measured: the session-log hook is not loaded in this session. Counts from the notes: one
batch of three steps wrote all the Java; two refusals, each followed by the fix it named.

## Assessment

Through the tools. The plan held. The decision (refuse, not read the step) is the smallest
option and is recorded in `docs/design.md`, Decisions, as a default to reopen.

## Follow-ups

- If a later friction entry shows the refusal still costing a turn, reopen option (b): read a
  step with no `args` by its other keys.
