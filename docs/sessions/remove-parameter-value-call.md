# remove_parameter: a call in the value

- **Item:** roadmap #148 (order 141): increment 2 of slice 4 of `docs/sessions/generators-sketch.md`,
  a call in `remove_parameter`'s `value`, declared first in the body as a local, with the
  effect-order and exceptional-behavior checks.
- **Date:** 2026-11-13
- **Branch:** remove-parameter-value-call

## Plan

#147 already declares the local and refuses a value with a call. So: goldens and probes first;
lift the refusal to assignments and lambdas only; say when a value acts (`Effects.changes`);
tighten the per-call position rule for a value that acts; teach the matcher a call. No
refactoring first: the checks are one loop in `removeWithExpression`.

## Notes

- plan: worked out before the code what moving a call from the argument list to the body can
  change: an argument after it now runs or is read before it; an argument the value reads is
  read once where the call read it twice, the second time after the value's own calls.
- plan: the sketch's rule (later arguments effect-free) lets through a later argument that reads
  a field the call changes. Stricter here: fixed. Recorded in `docs/design.md`.
- bulk: one batch of eight `replace_text` steps (six in `Signatures`, two in `ParameterValue`,
  about 190 lines, the matcher's call rule most of it).
- plan: a mismatch whose text equals the value's (a method of another object, another overload)
  had read "passes name(order), not name(order)"; it now says a name means something else.
- plan: goldens and probes were written before the code and first run after it; all passed on
  that run but the one whose wording changed.
- hand: none in `src/main`.
- refusal: none from the tools.

## Measured

Not measured: the session-log hook is not loaded here. From the notes: 1 batch (8 steps), 1
`replace_text`; 8 golden cases and 5 probes with file tools.

## Assessment

Through the tools. The plan held. The rules were found by reasoning about evaluation order, and
the probes then pinned them; no probe found a change the rules had missed, which is weaker
evidence than a stress run would be.

## Follow-ups

- `RefactoringStressTest` does not draw `remove_parameter` with `value`; the sketch's
  measurement (applied and refused by reason on Commons Lang) is not done.
- A value whose call is an overload chosen by an argument's type is refused with the "a name
  means something else" wording; it could name the two overloads.
