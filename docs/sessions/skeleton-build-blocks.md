# Skeleton-only checks each statement of the content

Branch `skeleton-build-blocks`, 2026-11-13. The user chose option 1 of three after the generator
A/B (`docs/sessions/ab-generators.md`): under `JREFACTOR_SKELETON_ONLY=1`, `insert_statements` and
`replace_statements` content had been checked only when it was one statement, so the invoice
task's typed locals (`Customer customer = order.customer();` before a `return`) passed.

## Change

- `Generated.checkBuild` reads every statement (`Generated.statements`; none when the content
  does not parse) and asks `Generated.built` for each one's entry. If any has one, it refuses,
  naming the first ("statement 1 of 3, a local declared from a call, ...") and one `build` to send:
  the entries, and the other statements as typed strings. One statement is refused as before.
- Golden: `insert_statements_skeleton_only_refused_in_block` (the old two-statement content, now
  refused); `insert_statements_skeleton_only_passes_two_statements` now holds two statements that
  `build` does not write (an assignment, an increment), and passes.

## Options set out

1. Check each statement; refuse on the first match, suggesting one `build` (chosen).
2. As 1, suggesting a `build` only when every statement is an entry.
3. Leave the rule; record the gap.

## Rerun

The skeleton arm on the depot tasks after merging: see the follow-up below.