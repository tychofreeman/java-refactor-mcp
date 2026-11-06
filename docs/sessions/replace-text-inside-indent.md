# replace_text: an old matched inside a line's indentation

- **Item:** roadmap #119, the first Java bug of gorefactor's `docs/parity.md` (friction log
  2026-11-05: `old` at 8 spaces matched a line at 10, so `new`'s lines went in at 8).
- **Date:** 2026-11-06
- **Branch:** replace-text-inside-indent

## Plan

An exact match of an `old` that starts with spaces, where the match starts inside a line's
indentation (only spaces before it on its line, and not at the line's start), is not taken as
exact: the #106 shift (`IndentShift`) finds it from the line's start instead and shifts `new`
with it, and the result says so. A match that starts at a line's start, or after code, is
exact as before.

Derivation: golden first (one line of `old`, two lines of `new`); then the filter on the exact
matches in `Edits.replaceText` (`replace_statements` of the search loop).

## Notes

- Golden first, red as the friction entry said (`return x;` at the old's indentation).
- plan: a refusal added for an `old` found only inside indentation that the shift cannot place
  ("old is not in" would have been false); golden `replace_text_refused_inside_indent`.
- read: only `Edits.replaceText`'s search (lines 143-166).

## Measured

Through jrefactor: 3 edit batches (5 steps) and the docs batch; no refusals; by hand the
goldens' fixtures (resources) and this record.

## Assessment

Through the tools; small, as planned, plus the honest refusal.

## Follow-ups

None.
