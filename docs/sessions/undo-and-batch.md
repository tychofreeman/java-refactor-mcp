# Undo and batch

- **Item:** roadmap #8, position 4 of the eight the user asked for (2026-11-02).
  Open questions 3 and 4 in `docs/design.md`.
- **Date:** 2026-11-02
- **Branch:** undo-and-batch

## Plan

At the highest level: a tool call is the unit of change today, and the model
often needs a different unit. Dependent steps (a signature and its caller) must
land together or not at all, and a change that turned out wrong must be
reversible without the model retyping the old code. So: a journal of what each
top-level call wrote (the before and after text of each file), `undo` that pops
it, and `batch` that runs several steps as one journal entry. Both follow
gorefactor's contract (`docs/tool-contract.md`, "batch").

Decisions (open questions 3 and 4), following gorefactor:
- Undo state lives in memory, per server process. Not on disk: undo is for the
  session's own changes, and git covers the rest. `undo` refuses when a file it
  would restore changed since (another edit, or a hand edit), naming the file.
- `batch`: `steps` (each `{tool, args, id?}`) and `check` (`"each"`, the
  default, or `"end"`). Line arguments (`line`, `start_line`, `end_line`) are as
  the model read them before the batch; the batch moves them past earlier
  steps' edits, and refuses a line an earlier step rewrote with a different
  line count. `"$prev.start"`, `"$prev.end"`, `"$2.end"`, `"$id.start+1"` name
  lines an earlier step changed. A refused step undoes the earlier ones, and the
  refusal names the step. With `"end"` the gate runs once after the last step
  (an edit may pass through code that does not compile). One undo reverts a
  whole batch.

Derivation:
1. Green, make the change easy: every write goes through `Gate` (the two
   non-Java writes in `Edits` call a new `Gate.write`), so one place can
   journal. And one runner for a call (argument check, then the handler), used
   by `Server` and later by `batch`. Green.
2. Red: `ServerTest`: an edit then `undo` restores the file; a second `undo`
   says there is nothing to undo; `undo` after a hand change refuses. Stub
   `undo`.
3. Green: `Journal` (begin, wrote, commit, abort, undo); `Gate` records; the
   runner begins and commits around a top-level call.
4. Red: golden cases for `batch`: two edits; a signature change and its caller
   with `"end"`, and the same refused with `"each"`; a line moved past an
   earlier step; a `$prev` reference; a line an earlier step rewrote, refused; a
   later step refused, so nothing changes. `GoldenTest` reads `<arg>.json` for
   `steps`. Stub `batch`.
5. Green: `Batch`: run each step through the runner inside one journal entry,
   translating line arguments and references; the deferred gate for `"end"`.
6. Docs: contract (`undo`, `batch` built), CLAUDE.md (the overload workaround
   goes), roadmap #8 Done.

## Notes

- `read:` the client's tool listing in this session again shows descriptions
  from before the restart (no `imports` argument, runs of spaces), while the
  running jar, rebuilt by the launcher at 16:40:27, has the new ones. The calls
  reach the new server; only the listing is old.

- `refusal:` none for step 1: `Gate.write` (add_declaration), the two non-Java
  writes moved to it (replace_text), `Tools.named` and `Tools.call`
  (add_declaration; it imported `java.util.Map` by itself, the first sign the
  #20 server was running), and the two callers (replace_text). Green.
- `hand:` none; `Journal` stub (create_file), `UNDO` (add_declaration), three
  `ServerTest` cases; red for the right reason.
- `refusal:` the `Journal` body (replace_declaration): `Path` ambiguous (three
  candidates); resent with `imports`. Then `ArrayDeque cannot be resolved`: the
  import fixing missed a name used only in a lambda. Answered by adding the
  import with replace_text, then the same call; it imported ten more by itself.
- `refusal:` a `replace_text` in `GoldenTest`: "old is not in". `cat -A` showed
  I had sent two spaces too many of indentation, reading `inspect`'s separator
  as code. The same cause explains #20's unexplained mismatch.
- `hand:` seven batch fixtures by shell (JSON steps). One fixture's expectation
  was wrong (a line I thought rewritten kept its text) and was corrected before
  the code ran.
- `refusal:` `Gate`'s deferral: the use and the declaration sent in one turn,
  use first; the use was refused. Resent after.
- `refusal:` the `Batch` body: `Pattern` ambiguous; resent with `imports`. Before
  resending I found my own bug in it (line translation used the blank-trimmed
  range) and fixed it in the content.
- `bulk:` `Journal` (~110 lines) and `Batch` (~210 lines), new behavior, each in
  one replace_declaration of a stub. All seven batch cases green on the first
  run.
- `plan:` the Haiku probe (signature change and caller): it chose `batch` with
  check "end" unprompted, and exposed a re-indent bug in `replace_declaration`.
  A red golden case (after one wrong fixture that changed the signature without
  the caller), then the fix in `reindent`.

## Measured

Not measured (roadmap #18). From the notes: all Java through java-refactor-mcp
(about 30 calls: create_file 3, add_declaration 9, replace_declaration 6,
replace_text 11, inspect 6); 6 refused, each answered with another call. No
Java by Edit, Write or shell. Fixtures and docs by shell.

## Assessment

- **Through the tools or around them:** through them, for all Java.
- **Large writes:** `Journal` and `Batch`, new behavior.
- **Did the plan hold?** Yes, in order. Added: the re-indent fix the probe found.
- **Reading:** `inspect` ranges only, plus one `cat -A` to see why an `old` did
  not match.
- **What went badly:** six refusals, none needing a hand edit: two ambiguous
  names (right, but with a poor example), one missed import (a bug), one
  indentation misread, one use-before-declaration ordering, and one Haiku-found
  re-indent bug. My own fixture mistakes: two.
- **One change that would have helped:** `replace_text` showing the nearest
  lines when `old` is not found (gorefactor does); it would have made the
  indentation misread obvious at once.

## Follow-ups

- Imports: a name used only inside a lambda was not detected as missing.
- Ambiguity refusals: suggest the likely candidate (a `java.*` one) first.
- `replace_text`: tolerate leading-space differences, and name the nearest
  lines when `old` is not found.
- `inspect`'s separator: consider a tab or `|` so indentation is unambiguous;
  measure first, as it changes every read.
