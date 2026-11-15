---
order: 500
item: 510
status: Done
---

## Item

`change_type` of a parameter or a return type: the gate's refusal says "changing the type of ...", not "changed the type of ...", since a refusal changes nothing (issue #643)

## Status

Done (2026-11-15): `SignatureChange.apply` takes `doing` for the gate's refusal beside `what` for the result; `Signatures.checked` passes it. Goldens `change_type_parameter_from_varargs_refused_calls`, `change_type_return_refused_caller` pin the wording. The follow-up is done too (2026-11-15): `add_parameter`, `remove_parameter` (also with `value`), `move_parameter`, `change_signature`, `extract_type` and `function_to_method` pass their own `doing` ("adding int retries to ...", "removing ...", "moving ...", "changing the signature of ...", "grouping ...", "making ... an instance method of ..."), pinned by the goldens `*_refused_compile`; the shorter `apply` overloads were inlined away, so every caller words both

## Evidence

Issue #643, found by the agent that fixed #614 (PR #633). `ChangeType` (the field route) already passed "changing the type of" to the gate. `add_parameter`, `remove_parameter`, `move_parameter`, `change_signature`, `extract_type` and `function_to_method` go through the five-argument `apply` with their result text ("added int retries to ... would add compile errors"): changed in the follow-up (session record `docs/sessions/signature-refusal-doing.md`)
