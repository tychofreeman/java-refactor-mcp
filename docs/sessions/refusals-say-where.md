# Refusals that say where

- **Item:** roadmap #110 (friction triage: one blind retry, 2026-11-02; one large file sent
  again, 2026-11-03), item 3 of the 2026-11-06 order.
- **Date:** 2026-11-06
- **Branch:** refusals-say-where

## Plan

1. Content that does not parse as members names the line of the content where it stops parsing,
   and that line's text.
2. A `create_file` refused for compile errors says it can go in a batch with check "end", with
   the steps that declare what it uses after it, rather than only "declare it first".

Derivation: goldens first; `Declarations.members` finds the first malformed or recovered node
and its line in the content; the `create_file` refusal's fix text (`replace_text`).

## Notes

- read: `Declarations.members`, `Jdt.parseMembers` and `parseSyntax`, the `create_file` refusal.
- plan: the malformed-node walk found nothing for `int c( {` (the body parse drops it unflagged);
  the line comes from the first syntax error of the content parsed as a class body instead, the
  node walk kept as a fallback.

## Measured

Through jrefactor: 3 edit calls and the docs batch; no refusals; by hand the goldens (resources)
and this record.

## Assessment

Through the tools. The batch-resend cost of 2026-11-05 (a refused step makes the whole batch be
sent again) is not this row: it needs a batch that keeps what passed, a design question.

## Follow-ups

- A refused batch step resent alone (friction log 2026-11-05): ask the user before designing it.
