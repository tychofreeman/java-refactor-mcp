---
order: 491
item: 501
status: Done
---

## Item

`inline` of one overload of a static method (`name` with its parameter types) found no call in
another file: it was refused with "has no calls in the project", or it inlined the calls of the
declaring file, deleted the method, and the gate refused the calls left elsewhere.

## Status

Done 2026-11-15: `InlineFunction.run` searched the files that hold the member as written,
`twice(int)`, which no call writes; it now searches by the method's name (`Qualified.name()`),
and the calls are matched to the chosen overload by binding key (`Bindings.same`) as before.

## Evidence

Issue #612; friction log (one-text-edit-apply, #571: `MemberMove.gate`, worked around by
renaming one overload first). Goldens `inline_function_overload_other_files` (calls of both
overloads in a second file) and `inline_function_overload_own_and_other_files` (calls in the
declaring file and another, an overload of another arity left), each failing before the fix.
