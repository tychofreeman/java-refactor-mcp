# Stubs travel in the call's result

- **Item:** roadmap #30, "Stubs travel in the call's result object, not
  `Stubs.said()` and a `ThreadLocal`" (decided 2026-11-03, `docs/design.md`
  Decisions). From the 2026-11-03 code review; the user asked for it.
- **Date:** 2026-11-03
- **Branch:** stubs-in-result

## Plan

The change, at the highest level: a tool call's result is an object carrying
its text and the stubs it declared, and each layer returns that object rather
than the stubs going into a shared collector that `Tools.call` drains.

Derivation, in order:

1. Golden cases already pin the stub text in `result.txt` (`stubs_*`,
   `batch_signature_end`). They stay as the check that nothing changes for the
   model (measure, before any code).
2. Add a unit test that calls a stub-declaring path and reads the stubs from
   its returned value, not from `Stubs` (red: the value does not exist yet).
3. Make the stub-declaring function return its declared stubs instead of
   recording them (`Stubs.declare` returns the list); the gate passes them up
   (`Gate.apply` returns them with the change). Green.
4. Each handler returns the result object (text plus stubs), so `Tools.call`
   reads the stubs from it and `Stubs.said()`, `DECLARED` and the ThreadLocal
   go. Refusal paths return nothing and so carry no stubs (a refused call
   changes nothing, and its stub declarations are undone with it).
5. Refactor while green: remove the now-unused side channel.

Behavior change: none for the model; the result text is the same.

## Notes

## Measured

Not measured (the session log did not record this session).

## Assessment

## Follow-ups
