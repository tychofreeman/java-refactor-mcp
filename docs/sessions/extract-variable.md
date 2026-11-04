# extract_variable (the first of the ten higher-level tools)

**Item:** a tool that names an expression: a declaration is inserted before
its statement and the name is used in place of the expression.
**Source:** the composite comparison of 2026-11-04 (Extract Variable, Replace
Temp with Query, Introduce Variable), and the user's choice of option B, with
`extract_variable` and `inline_variable` first (2026-11-04).
**Date:** 2026-11-04

## Plan

Change at the highest level: an expression is named by the line it starts
on and its source text (column when the text is on the line twice), and the
tool writes a declaration and the name in one edit under the gate.

Operations, in order:
1. Find the expression node on the line whose source text matches, or
   refuse when there is none, or several and no column.
2. Find the statement that contains it, and refuse unless that statement is
   directly in a block (not the body of an `if` or loop with no braces).
3. Refuse when the expression is evaluated only sometimes or more than once:
   inside a loop's condition, or below a `&&`, `||` or `?:`.
4. Declare `Type name = <expression>;` with the type of the expression, at
   the statement's indentation, before the statement; replace the expression
   with `name`. Both are text edits under the gate (`Edits.gated`).

Arguments (as gorefactor's `extract_variable`): `file`, `line`, `expression`,
`name`, optional `column`. `all_occurrences` and `pointer` are not in this
first PR.

## Golden cases (written first)

- `extract_variable_simple`: `price * qty` on a return.
- `extract_variable_refused_loop_condition`: refused, "loop condition".
- `extract_variable_refused_ambiguous`: refused, "occurs 2 times".

## Notes

- `hand:` the three golden cases' `in/` and `out/` files, written with
  `Write`. The tool that would do it is this one; it did not exist yet.

## Measured

Not measured: the session log hook runs from the jar, and this session's tools
are the build from before this branch, so the new tool was not called through the
server. Its goldens and the full `verify` were run with `./mvnw`.

## Assessment

- **Through the tools or around them:** the implementation and the golden cases
  were written by hand (`Write`, the `Edit` tool), since the tool is the thing
  being built. There is no server call to compare.
- **Plan held:** the operations named above are the ones in `ExtractVariable`:
  find, enclosing statement, place checks, then the declaration and the
  replacement as two text edits under `Edits.gated`.
- **What went wrong on the way:** the golden case line numbers were wrong in
  the first run (the statement was one line below what I wrote), so the refusal
  said "no expression starts on line N". The test caught it; the fix was in the
  case, not the code. Also, `ASTVisitor.preVisit` did not visit every node, so
  the matches were empty; `preVisit2` is the one that returns `true` for each.
- **One change that would have helped:** an option on `replace_text` or a
  `column`-first address in the goldens' `args.properties`, so a golden line
  number is not counted by hand. A friction-log entry is still to be written.

## Follow-ups

- `all_occurrences`, `pointer`, and inlining (`inline_variable`, next PR).
