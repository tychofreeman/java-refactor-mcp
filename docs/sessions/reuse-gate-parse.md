# Family finds its methods without parsing the project

- **Item:** the performance item of the code review, roadmap order 44
  (review row 51): each gated call re-parses; `Signatures.Family.of`
  parses every file before the gate. Taken from the user's "Do #44" with
  the "Go ahead and do the measurement" of 2026-11-03.
- **Date:** 2026-11-03
- **Branch:** reuse-gate-parse
- **Numbering:** the user's numbers were read as the table's order column
  for this item (44). The `#` column reads 51 for it. The summary of the
  session says #44; this record follows that.

## Plan

Change: `add_parameter` (and the other signature tools) find the family of
a method without parsing files that cannot contain it.

Operations, in order:
1. Read the method's simple name from `name` (before `(`, after the last
   `.`), which needs no parse.
2. Read every file's text (as now), keep the files whose text contains that
   name, and parse only those (`Jdt.parseAll` over the candidates). The home
   file is one of them, so `Declarations.find` runs as now.
3. `Signatures.family` and `findCalls` run over the candidates, as now.

Why this is exact: a method in an override family, or a call of one, has
the same simple name as the method. A file without that name cannot declare
or call a family member, so its parse cannot change the family.

The one edit that changes behavior: `Family.of` parses candidates, not all
files. Its result (the same family and calls) is the same; the gate's
own parse (`TypeCheck`) is unchanged.

Not done: reusing the gate's parse across calls, which the summary's plan
named. It would keep every AST of the project between calls (about 990 files
here), which costs memory on every edit; the prefilter removes the parse
outright. Recorded as the option not taken.

## Notes

- `read:` `Signatures.Family.of` and `family`, to see what the family needs
  from files: declarations and calls of the same name.
- `plan:` the summary's plan (reuse the gate's parse) replaced by the
  prefilter; reason in the Plan section.
- `hand:` `Signatures.java` changed with the Edit tool. The java-refactor-mcp
  tools were not in this session's tool list, so `replace_text` could not be
  used. The friction log records it.
- `refusal:` none.

## Measured

Commons Math 3.6.1 (`/…/scratchpad/cm/src`), `GateCostTest.timesACall`,
`add_parameter` of `label` (String) on `MathUtils.checkFinite(double)`, one
declaration and one call, cold project, one run each:

- before: 6488 ms, gate parses 1 (plus `Family.of`'s parse of every file).
- after: 2980 ms and 2892 ms (two runs), same result text. The gate counts 1
  parse; `Family.of` now parses only the files whose text contains `checkFinite`
  (the home file and the `LoessInterpolator` call), not all 990.

Session log: not measured (no `.claude/session-log/reuse-gate-parse.jsonl`).


## Assessment

- **Through the tools or around them:** around, once. `Signatures.java` was
  changed with the Edit tool; `replace_text` would have made the same edit, but no
  java-refactor-mcp tool was in this session's tool list.
- **Large writes:** none. The new helper `simpleName` is new code (8 lines).
- **Did the plan hold?** Yes, with one change: the summary's plan (reuse the
  gate's parse) was replaced by the name prefilter. Reason: reusing it would keep
  every AST of the project between calls; the prefilter removes the parse.
- **Reading:** `Family.of` and `family` only, to find what a family needs from
  files.
- **Not tested directly:** no red test. The change is behavior-neutral, and
  the 11 signature goldens (including `sig_add_family`, which spans files) and
  the full verify run cover it. A golden where the family spans a file that
  has the name only inside a comment would show the extra parse is harmless.
- **One change that would have helped:** the java-refactor-mcp tools in the
  session, so this edit went through `replace_text`.

## Follow-ups

- The reuse across calls (the summary's option) is not built; if a later
  measurement shows the candidate parse still dominates, revisit with the
  memory cost in view.
