---
order: 130
item: 137
status: Done
---

## Item

A functional interface's contract (what its method throws, its return type) changed across its lambdas and method-referenced methods, the calls left to an edit in the same batch; first step: carry a new `throws` to every implementation

## Status

Done 2026-11-12 (`change_signature`, `ChangeSignature`; the user asked for it, reversing roadmap-only, and chose: a new tool over options on `change_type`; implementations follow and calls are left to the batch, over declarations only and over propagating `throws` to callers; a removed `throws` a body still needs is refused, over removing it only where unused; then, 2026-11-13, `callers`: a new checked exception is propagated to the callers by default, with `stop_at`, or caught at each call, or refused as before)

## Evidence

Issue #342: done with four `replace_declaration`s in one batch
