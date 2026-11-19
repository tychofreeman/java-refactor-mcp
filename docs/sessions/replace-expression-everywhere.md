# replace_expression everywhere, with $name holes

- **Item:** the user, 2026-11-19, from "what's next?": the last parity gap found that day with
  gorefactor's recent PRs, its #676 (`everywhere` and `$name` holes on `replace_expression`).
  Its A/B (cost-history 30 and 33 there): freight-xxl routes went from 628 per-use edit steps in
  8 runs, 6/8 passed, to 153 steps, 8/8. The batch anchoring of its #690 is a later step.
- **Date:** 2026-11-19
- **Branch:** replace-expression-everywhere

## Plan

The change: `replace_expression` becomes a typed counterpart of `sed -i` over the project. A
pattern (`expression` with `$name` holes) is anchored at `line`; the anchor fixes what each other
name means and each hole's type; with `everywhere`, every expression of the project that matches
the same way is replaced, `new` getting each hole's source.

- **The easy change:** in `Primitives.replaceExpression`, before the single-occurrence path,
  `if (args.flag("everywhere") || ExpressionPattern.hasHoles(expression)) return
  ExpressionPattern.run(project, args, opened);`. One place; the rest is a new class, so no
  refactoring first.
- **The places today:** `Primitives.replaceExpression` (the route), `Tools.REPLACE_EXPRESSION`
  (the argument and a sentence), `Jdt` (no expression parser: `parseExpression`, syntax only, as
  `parseMembers` is). `Fold.Holes` is a matcher with holes, but it compares two resolved trees;
  here the pattern is syntax only, matched by identifier at the anchor and by binding elsewhere,
  so it is not shared (as `ParameterValue` did not share it).
- **The new class, `ExpressionPattern`:**
  - `parse` the pattern; the holes in order of first use; `new`'s holes, each exactly once and in
    that order, refused otherwise (gorefactor's rule: it would evaluate them differently).
  - `anchor`: the expressions on `line` that match by syntax (holes take any expression, a hole
    twice the same text), outermost first, `column` to choose; it binds each hole's type and each
    other name's binding key, or for a local or parameter its type.
  - `everywhere`: `Holding` of the files naming the pattern's first non-hole name (every file when
    it has none); each expression of the pattern's node type matching by binding, holes by type;
    a match's inner expressions are not looked at again.
  - `written`: `new` with each hole's source, parenthesized unless primary; the whole of `new`
    parenthesized when it is not primary and the place needs it.
  - All files through `Edits.gatedFiles`, so imports `new` needs are added.
- **Tests first (goldens):** `replace_expression_everywhere` (a record accessor rewritten across
  two files, another type's accessor of that name untouched), `replace_expression_hole_once`
  (refused: a hole `new` uses twice), `replace_expression_hole` (holes without `everywhere`: the
  anchor only).

## Notes

- bulk: ExpressionPattern (428 lines) by create_file in a batch: new behavior, no existing code typed again; the one route in Primitives.replaceExpression and the argument were small replace_text steps.
- refusal: a batch create_file with content "@pattern" was refused as not parsing: sections are the CLI's, not the MCP server's; resent with retry and the text inline.
- hand: the golden fixtures (`src/test/resources/golden/replace_expression_*`, Java under
  `resources/`) were written with shell heredocs and `sed`, not `create_file`: a fixture is no
  project source, and heredocs wrote several files a call.
- plan: the first `everywhere` golden failed because its fixture's `Routes.of` read `s.road()`
  itself, which `everywhere` rightly rewrote into a call of itself; the fixture changed, and the
  case is in the contract and the follow-ups.
- plan: two definition trims (1,710 then 1,702 characters) to stay under 1,700.

## Measured

`bin/session-report` on this branch:

- tool calls: 34; through jrefactor: 2 edit steps (the batch counted as its edits), 1
  `bin/jrefactor` call in Bash; refused by jrefactor: 2 (the batch's `@pattern`, an `inspect`
  of a name that did not exist yet).
- by hand: 7, all fixtures and docs (heredocs, `python3`, the record's `Write`); no Java of
  `src/main` or `src/test/java`.
- large writes: 0 reported, though `create_file` of `ExpressionPattern` wrote 428 lines inside a
  `batch`: the report does not look inside a batch's steps for large writes, as gorefactor's
  `refactor-tokens` did not (its #676 record).
- reads: inspect 11.

## Assessment

- **Through the tools:** every line of `src/main` went through `create_file`, `add_declaration`
  and `replace_text`, as a new class goes in. The route was one
  `replace_text` narrowed by `function`.
- **Large writes:** one, `ExpressionPattern`, all new behavior; no existing code was typed again
  (`Fold.Holes` was read and not reused, for the reason in the plan).
- **Did the plan hold?** Yes: one route, one new class, `Jdt.parseExpression`, the argument.
  The goldens named in the plan changed names (`replace_expression_refused_hole_twice`), and
  one was added for parentheses and hole types (`replace_expression_everywhere_parens`).
- **Reading:** `Primitives.replaceExpression`, `ExtractVariable.find`, `Edits.gatedFiles`,
  `Holding`, and the `Opened` and `Nodes` helpers' signatures: what the new code calls.
- **One change that would have helped:** `bin/session-report` counting a large write inside a
  batch step; gorefactor has the same gap. Friction log, 2026-11-19.

## Follow-ups

- gorefactor's #690: a batch step's expression found again by its text on a line an earlier
  step rewrote (needs `everywhere` first; now possible).
- A match inside the method `new` calls is replaced too, making it call itself; a refusal, or
  leaving such a match with the reason, as `fold` leaves matches.
- `bin/session-report`: large writes inside a batch.
