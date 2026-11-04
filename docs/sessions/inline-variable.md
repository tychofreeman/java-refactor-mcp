# inline_variable (the second of the ten higher-level tools)

**Item:** replace every use of a local variable with its initializer, and delete
its declaration. The inverse of `extract_variable`.
**Source:** the composite comparison of 2026-11-04 (Inline Variable, Replace Temp
with Query), option B of the 2026-11-04 choice, `inline_variable` after
`extract_variable` (the user's "go ahead", 2026-11-04).
**Date:** 2026-11-04

## Plan

Change at the highest level: a local variable is named by file, line and name
(at its declaration or any use), and the tool rewrites its uses and removes the
declaration in one edit under the gate.

Operations, in order:
1. Find the variable's declaration from the name and line. Refuse a field, a
   parameter, a declaration of several variables, or one with no initializer.
2. Refuse when the initializer calls, assigns or changes a variable (its
   evaluation could move), when the variable is assigned after its declaration,
   or when a variable the initializer reads is assigned after the declaration.
3. Refuse when the declared type differs from the initializer's type.
4. Replace each use with the initializer, in parentheses unless it is a primary
   expression (a name, a literal, a call or a field access).
5. Delete the declaration's line, when the statement is alone on it.

Arguments (as gorefactor's `inline_variable`): `file`, `line`, `name`, optional
`column`. `function` (inline by a function's name) is not in this first PR.

## Golden cases (written first)

- `inline_variable_simple`: `subtotal` used once in `return subtotal + tax`.
- `inline_variable_refused_assigned`: refused, "assigned after its declaration".
- `inline_variable_refused_calls`: refused, "calls, assigns or changes a variable".

## Notes

- `hand:` the golden cases' `in/` and `out/` files, written with `Write` (the
  tool is the thing being built).

## Measured

Not measured through the server: this session's tools are the build from before the
branch. The goldens and the full `verify` were run with `./mvnw`.

## Assessment

- **Through the tools or around them:** the implementation and the golden cases were
  written by hand (`Write`). The tool is the thing being built, so no call existed to
  compare.
- **Plan held:** the five operations match `InlineVariable`: find the declaration, check
  the place and the value (assignments, effects, types), rewrite the uses, delete the
  line. The effect check is `ExtractVariable.hasEffect`, now shared.
- **Refusal wording:** the first version refuses any initializer with calls, which is
  stricter than gorefactor (it allows one use with nothing between). That is a choice for
  this first PR; the follow-up can relax it when a case needs it.
- **Read:** `InlineVariable` needs `Lines`, the binding of each name and the place of each
  use, all from the parse. No body was read beyond the test files.

## Follow-ups

- Inline by function name (`function`), and inlining a variable whose
  initializer has calls when it has a single use with nothing between.
