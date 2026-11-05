# Ambiguous imports: choose on evidence, refuse with every name

- **Item:** roadmap #105, from the friction triage (`docs/sessions/friction-triage.md`):
  six entries where an ambiguous simple name cost a resend. The rule is the user's
  choice of 2026-11-05 ("choose when evidence"), over choosing only a prior import
  and over never choosing.
- **Date:** 2026-11-05
- **Branch:** ambiguous-imports

## Plan

The import fix decides an ambiguous name itself when the file gives evidence, and
otherwise refuses once for all such names. The evidence, in order: a candidate the
file imported before the call or the batch (its text in `Journal.written`, else the
call's `before`), then the one candidate whose package the file already imports a
class from. A choice is named in the result. A refusal lists every ambiguous name,
with the suggested candidate a `java.*` one when there is one.

Derivation, in `Imports` (outline only):

1. Goldens first (red): `imports_chosen_by_package` (`List` in a file that imports
   `java.util.Map`), `imports_chosen_prior_in_batch` (a batch whose first step
   removes the last use of `Date` and the second uses it again), and
   `imports_refused_every_name` (`Date` and `List` in one edit).
2. `toImport` gains the evidence (prior imports, imported packages) and a place to
   say what it chose: `add_parameter` twice, through its two callers in `fix`.
3. The behavior: in `toImport`, the choice before the refusal, and the refusal
   collected across names and thrown after the loop (`replace_declaration` of
   `toImport`, a small method).
4. `fix` computes the evidence and adds the choice notes (`replace_text`).
5. The tool descriptions that say an ambiguous name is refused (`replace_text`), and
   `docs/tool-contract.md`.

## Notes

- read: `Imports` outline, then `fix`'s body (to write the evidence), and
  `Journal.wrote`/`written` and `Edits.gated` (whether `written` keys by the same path
  as `fix`'s `file`).
- hand: the three goldens' fixtures by heredoc (under `resources/`, allowed); one
  case's `List` turned out not to be ambiguous (`java.awt.List` is not indexed), so
  two cases were rewritten around `Date` and `Array`.
- plan: the `add_parameter` steps were dropped: with the goldens red, the next step
  had to make them pass, so the signature and the behavior went in one batch of
  edits (`replace_declaration` of `toImport`, `add_declaration` of `chosen`,
  `packageOf`, `importsOf`, three `replace_text`), checked at the end.
- bulk: `toImport` (56 lines) is the old method with the choice and the collected
  refusal; about half is its old body written again. A refactoring (`add_parameter`
  twice, then the edit) would have moved it, but only before the tests were red.
  `chosen` and its helpers (45 lines) are new behavior.
- refusal: `replace_text` with `append` and an empty `old` ("leave out old"): resent
  without it.
- refusal: a `replace_text` whose `old` I wrote at 10 spaces after the block had moved
  to 12; resent from `inspect`.
- The description edit's `old` matched inside the line's indentation and put the new
  lines two spaces short; Spotless shifted the block and `ServerTest` failed; two more
  `replace_text` calls put it back (friction log, 2026-11-05).

## Measured

`bin/session-report` for the branch: 33 tool calls; through jrefactor 0 refactoring
steps and 9 edit steps; 9 `bin/jrefactor` calls in Bash (reads); refused by jrefactor
2; by hand 2 (the golden fixtures, under `resources/`); refused, then done by hand 0;
large writes 4 (the two fixture heredocs, 71 and 21 lines of fixture Java; the
`toImport` replacement, 56; the new helpers, 45); denied 1 (the report's first run);
reads: inspect 3.

## Assessment

- **Through the tools or around them:** every change to the server went through the
  tools; the only text by hand was fixtures, which the guards allow.
- **Large writes:** the `toImport` replacement wrote about 25 lines of its old body
  again. Planning the `add_parameter` steps before writing the goldens (refactor
  green, then red) would have avoided it; the plan named them, but in the wrong place
  in TDD's order.
- **Did the plan hold?** The goldens, the behavior in `toImport`, the evidence in
  `fix`, and the description held; the signature change was folded into the edit.
- **Reading:** `fix`'s body, to place the evidence; nothing read to check safety.
- **One change that would have helped:** `replace_text` matching `old` only at a
  line's start when `old` starts with spaces (friction log; with roadmap #106).

## Follow-ups

- Roadmap #106 (replace_text near misses) now has a third entry.
