---
order: 500
item: 510
status: Done
---

## Item

`change_type` of a parameter or a return type: the gate's refusal says "changing the type of ...", not "changed the type of ...", since a refusal changes nothing (issue #643)

## Status

Done (2026-11-15): `SignatureChange.apply` takes `doing` for the gate's refusal beside `what` for the result; `Signatures.checked` passes it. Goldens `change_type_parameter_from_varargs_refused_calls`, `change_type_return_refused_caller` pin the wording. The other signature tools still pass one past-tense string (follow-up below)

## Evidence

Issue #643, found by the agent that fixed #614 (PR #633). `ChangeType` (the field route) already passed "changing the type of" to the gate. `add_parameter`, `remove_parameter`, `move_parameter`, `change_signature`, `extract_type` and `function_to_method` go through the five-argument `apply` with their result text ("added int retries to ... would add compile errors"): not changed here, a default to reopen
