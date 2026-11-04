# change_type (the fourth of the ten higher-level tools)

**Item:** change the declared type of a field, so its uses must still compile.
**Source:** the composite comparison of 2026-11-04 (Change Type, Change Field Type,
Replace Primitive with Object), option B, after the three tools built so far
(the user's "go ahead", 2026-11-04).
**Date:** 2026-11-04

## Plan

Change at the highest level: a field is named `Type.field` and given a new type.
The tool replaces the type text of its declaration, and the gate checks every
use in the project; a use that no longer compiles is refused and nothing changes.

Operations, in order:
1. Find the field `Type.field` in a top-level class of the file. Refuse a method,
   a missing field, and a declaration of several fields (`int a, b;`).
2. Replace the declaration's type text with the new type, in one text edit,
   through `Edits.gated` (imports are fixed for the new type, and the gate runs).

Arguments: `file`, `name` (`Type.field`), `type` (the new type as written).
Not in v1: locals, parameters, return types (`add_result` and
`change_parameter_type` in gorefactor), and fields declared with several names.

## Golden cases (written first)

- `change_type_field_simple`: `int total` becomes `long total`; every use still compiles.
- `change_type_refused_uses`: `int total` to `String` breaks `total + amount`; refused,
  "would add compile errors".
- `change_type_refused_not_field`: `Price.get` is a method, not a field; refused,
  "is not a field".

## Notes

- `hand:` the golden cases' `in/` and `out/` files, written with `Write` (the tool
  is the thing being built).

## Measured

Not measured through the server: the session's tools are the build from before this
branch. The goldens and the full `verify` were run with `./mvnw`.

## Assessment

- **Through the tools or around them:** the golden cases were written by hand (`Write`).
  The tool is the thing being built.
- **Plan held:** one text edit of the field's type through `Edits.gated`; the gate did the
  checking of the uses. No separate use search was needed.
- **What went wrong on the way:** the first success case was my mistake, not the tool's.
  The getter returned `int`, so returning the `long` field did not compile, and the gate
  refused it. The gate was right; the input had to change.
- **Read:** the field declaration and its type, from the parse; no body read.

## Follow-ups

- Locals and parameters (by line and name); return types; fields with several names.
