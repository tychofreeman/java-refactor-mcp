---
order: 139
item: 146
status: Done
---

## Item

Generics, the shared check and wildcards (increment 3 of `docs/sessions/generics-sketch.md`): a change keeps what every call runs, and `change_type` with a wildcard is pinned

## Status

Done 2026-11-13 (`CallTargets`: each call, `new`, `this(...)`, `super(...)` and method reference in the checked files runs, after the change, the declaration it ran before, known by its place among its file's methods, not by its key; read before the write and required after it, as `Rename.checkMeanings`, so it also runs in a batch whose gate is deferred, leaving a call that does not resolve to the end check. `change_type` calls it for a field and a method; the gate's fix line names what a wildcard does not allow. The user, 2026-11-13: the model writes the wildcard in `type`, over `wildcard: "auto"` and over a body analysis)

## Evidence

A review found generics a gap; the friction log has no entry asking for it (the sketch says so)
