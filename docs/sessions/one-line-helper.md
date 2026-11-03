# One line helper

- **Item:** roadmap #27 (row 20 of the table): the duplicated line helpers
  and the "line N" text, from the code review of 2026-11-03.
- **Date:** 2026-11-03
- **Branch:** one-line-helper

## Plan

One helper class, `Lines`, owns line arithmetic and line wording. The
change is: create `Lines` (red test first, `LinesTest`), then point each
duplicate at it and delete the copies. The one edit that changes behavior
is none: the wording stays the same, so every golden case is unchanged.

Operations, in order:
1. `LinesTest` (red: no `Lines` class).
2. `Lines` with `number`, `start`, `end`, `range`, `list`.
3. Declarations: remove `lineStart`/`lineEnd`, call `Lines`.
4. Imports, Edits (`lineOf`), Tools (`lines`), Rename, Extract: call `Lines`.
5. Remove the copies; the compiler finds any missed call.

## Notes

- `hand:` `Lines.java` and `LinesTest.java` written with Write. New code,
  about 50 lines. A tool could not create them without a file create; the
  `create_file` edit would have made the same new file.
- `hand:` the six call-site files changed by a Python script with regex
  renames and exact anchor replacements. `replace_text` would cover each file
  in one call; `batch` with check "end" would cover all six. Not tried: the
  script was faster to write, which is the reason, and the friction log has
  the entry.
- `plan:` the roadmap row named `Inspect` and `SessionReport` as holding
  copies. They do not; their "line" text is different (a range label in
  `Inspect`'s doc comment, `handLines` in `SessionReport`). The row was
  corrected in this PR and the reason is in session-notes.
- `refusal:` none.
- `read:` `Declarations` and `Extract` read at the lines with the copies, to
  see the call sites before the script ran.

## Measured

Not measured. No session log exists for this branch
(`.claude/session-log/one-line-helper.jsonl` is absent), so
`bin/session-report` has nothing to report. The session started before the
hook loaded. Counts from the notes: 0 java-refactor-mcp calls for edits,
about 4 hand writes (Lines, LinesTest, and two Python edit scripts).

## Assessment

- **Through the tools or around them:** around. The six call-site edits were
  Python scripts, not `replace_text` or `batch`. The gap is real: the tool
  exists and the server was not running in this session, so the edits could
  not go through it. The friction log entry names `batch` with check "end".
- **Large writes:** `Lines.java` is new behavior (the helper itself), not
  copied code. No large write moved existing code by hand.
- **Did the plan hold?** Yes. Operations 1 to 5 were done in that order; the
  compiler caught no missed call (the script's count assertions caught two
  anchors that matched twice, and the fix was to replace both explicitly).
- **Reading:** `Declarations` and `Extract` bodies, to find the copies.
- **One change that would have helped:** the server running in the session,
  with `batch` and check "end", so the six-file rename is one call. Recorded
  in the friction log, 2026-11-03 entry.

## Follow-ups

- The row 27 spec no longer names `Inspect` and `SessionReport`; no change
  needed there.
- Next in the user's order: #44 (reuse the gate's parse in
  `Signatures.Family.of`), then #25, then #28.
