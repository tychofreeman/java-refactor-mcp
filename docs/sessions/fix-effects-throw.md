# Effects: a throw is an exit, and what else may throw

- **Item:** issue #474 (code review, extract-inline #13): `Effects` had no effect for
  `throw <variable>` or `assert`, and `Effects.leaves` ignored `throw`, so `count++` moved past
  `if (n == 0) throw err;` was neither refused nor reported.
- **Date:** 2026-11-13
- **Branch:** fix-effects-throw

## Plan

The change: `Behavior`'s exit rule sees a throwing guard. Derivation: probes first (an effect moved
up and down past the guard, a division moved above it), then in `Effects`: `MAY_THROW` for a
`throw`, an `assert`, an integer `/=` and `%=`, and an unboxing; `leaves` true for a throw no try
inside the statement catches. No change to `Behavior`: its exit rule (`checkExits`) already
refuses an effect crossing an exit and judges code that may throw by `Guards`.

## Notes

- read: `inspect` of `Effects` (outline, then `of` and `leaves`), `Behavior.flow`,
  `checkCrossed`, `checkExits`; `pattern` for the callers of `leaves` and `mayThrow`.
- plan: the three probes failed on main (the program printed another count, or another exception).
- `Effects`: one `batch` (three `replace_text`, one `add_declaration` of `caught` and `isIntegral`);
  `replace_declaration` of `caught` twice (a raw `UnionType.types()` list, which `-Werror` would
  fail on; the second follows `Extraction`'s loop); `replace_text` for the unboxing visit and the
  `leaves` comment. `EffectsTest`: `add_declaration` of a source, a helper and two tests, then
  `replace_text` of the helper's predicate (two statements start on a line with an `if ... throw`).
- Every golden case (814) and probe kept its output with each step, the unboxing included: no
  `exceptional.txt` changed.
- plan: `LoopToStream` also asks `leaves`, and refuses a body that leaves ("a break, continue or
  return ..., which a lambda cannot do"); a lambda can throw, so it should not see throws.
  `add_parameter` `throwing` (value `true` at the five calls) in one `batch` with the body's use
  and `LoopToStream`'s call changed to `false`: that tool behaves as before.
- hand: golden and probe resource files, and the docs (plain files, as the brief says).

## Measured

Not measured: the session-log hook is not loaded here.

## Assessment

Through the tools: every Java change went through `bin/jrefactor`. The plan held; `Behavior` needed
no change; `LoopToStream`, the other caller of `leaves`, keeps its behavior through `throwing`.

## Follow-ups

- A throw caught only by a catch of a subtype of its static type counts as leaving (conservative).
