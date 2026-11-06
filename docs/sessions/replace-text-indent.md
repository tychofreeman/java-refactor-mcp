# replace_text: an old off by its indentation

- **Item:** roadmap #106, from the friction triage (two misses on 2026-11-02) and three more
  since (#115, #118 twice): `old` copied from `inspect` with the two-space separator counted as
  indentation.
- **Date:** 2026-11-06
- **Branch:** replace-text-indent

## Plan

`replace_text` finds `old` exactly; when it is not there, it accepts an `old` whose every
non-blank line is off by the same indentation and then matches once, and shifts `new` by the
same amount, so the text lands at the file's indentation; the result says so. Spacing within a
line never loosens (a text block's spaces are content). When nothing matches, the refusal names
the lines of the file where a line of `old` is, spacing ignored, as gorefactor's `lineHint`.

The user's choice (2026-11-06), over gorefactor's rule (any run of spaces within lines matches
any other, `new` written as sent: leaves `new` at the wrong indentation and can match inside a
text block) and over refusing with a hint only (every miss still costs a resend).

Derivation:

1. Goldens first (red): `old` and `new` 2 spaces too deep; `old` 2 spaces too shallow; a miss
   whose first line is in the file (the hint).
2. In `Edits`' replace_text: a `Shifted` lookup (line by line from each line start) after the
   exact search finds nothing, and a hint in the refusal; `add_declaration` of the helpers,
   `replace_text` of the call site.
3. Docs: the description, the contract, the roadmap, the notes' trap.

## Notes

- read: `Edits.replaceText` (where the exact search and the refusal are); gorefactor's
  `textutil.Locate` and `lineHint` (the rule ported, and the hint's wording).
- hand: three goldens' fixtures by heredoc (under `resources/`).
- `IndentShift` began as `create_file` with `declaration` (a record), its members by one
  `add_declaration`, and the call site by `replace_statements` and two `replace_text`, all in one
  batch: no refusal.
- bulk: `IndentShift`'s members (128 lines): new behavior.
- bulk: the call site (28 lines, `replace_statements`): 7 of them the old search carried again,
  the rest new.
- The test started from `test_for` (`IndentShift.find`), the slice's first use of it in this
  project; its `fail` became assertions, and #117 imported `assertEquals` and `assertNull`
  statically where it once stubbed them.
- A `head` of the new test was denied by the read guard; `inspect` `pattern` instead.
- The hint said "line line 8" (`Lines.list` names the word itself); fixed by
  `replace_statements`.

## Measured

`bin/session-report` for the branch: 25 tool calls; through jrefactor 0 refactoring and 14 edit
steps; refused 0; by hand 1 (this record; the fixtures were in the same call); large writes 4
(this record, `IndentShift` 128 lines, the call site 28, the test 20); failed 2 (a test run with
the hint's typo, and the guard's denial of `head`).

## Assessment

- **Through the tools:** all Java through the tools, built at the intended level: the type from
  its declaration, the test from its target, members added; nothing typed twice.
- **Large writes:** new behavior, but for the 7 search lines carried into `replace_statements`.
- **Did the plan hold?** Yes, plus a unit test for the edges (two matches, uneven shifts,
  spacing within a line, the first line of a match inside a line).
- **One change that would have helped:** none needed; this item removes the trap that cost the
  last two.

## Follow-ups

- A tab-indented file: indentation counts spaces only, so a shift there is not found; no case yet.
