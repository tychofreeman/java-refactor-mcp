# surround refuses to hide a local a later statement reads

- **Item:** #611 (filed from an agent's session on #481): `replace_statements` with `surround`
  moved a local's declaration into the new block while a later statement read it; the edit
  then declared a field stub for the name, so the result compiled and meant something else.
- **Date:** 2026-11-14
- **Branch:** surround-hidden-local

## Plan

The change: surrounding statements changes the scope of what they declare, so it is only
meaning-preserving when nothing after the range reads those declarations. That is a
precondition of the edit, checked once for both forms of `surround` (a head, or a loop object).

Derivation: no refactoring first; the change is one check before the existing code. Red: a
refused golden case, `replace_statements_surround_refused_later_read`. Green: a method,
`requireNoLaterRead`, that collects the binding keys of the locals the range declares and
refuses at the first later name that binds to one; called when `surround` is present. Then a
probe, as the issue asks, since the silent case is the point.

## Notes

- read: `replaceStatements` (lines 126-250), to find where the range and `surround` are known.
- read: three surround golden cases and one refused probe, for the formats.
- hand: the golden case and the probe (resources, outside the guards).
- plan: the batch inserted the call after `Object around = ...` rather than before it (the
  line given was read before the batch added four imports above it); after is as good, and the
  next step used `around` in place of a second `args.raw("surround")`.

## Measured

Not measured: a cloud clone with no MCP server and no session-log hook. Java went through
`bin/jrefactor`: one `batch` (`add_declaration` of the method, `insert_statements` of the call,
checked at the end), `replace_expression` for the tidy-up, `replace_text` for the contract.

## Assessment

Through the tools: no Java by hand outside resources. The plan held. The probe was checked both
ways: without the fix it reported the behavior change (2 became 5, the field read), with the fix
the call is refused.

`new java.util.LinkedHashMap<>()` is written qualified, since the content named it so; an
import would read better. A local class declared in the range (`TypeDeclarationStatement`) is
not checked; nothing has asked for it.

## Follow-ups

- The issue's other option, keeping the declaration before the new block and assigning inside,
  is not built: the refusal tells the model to do that itself.
