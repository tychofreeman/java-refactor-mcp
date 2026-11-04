# A primitive loop variable over a stub

- **Item:** the follow-up of `stub-enhanced-for.md` (#68): a primitive loop
  variable (`for (int n : items())`) gave `List<int>`, which does not
  compile, so the edit was refused. Chosen by the user from `what's next?`
  and this request, 2026-11-04.
- **Date:** 2026-11-04
- **Branch:** stub-enhanced-for-primitive

## Plan

The change, at the highest level: the element type of a stub's `List` is
the loop variable's type, boxed when that type is primitive, because a
`List` holds objects.

Operations, in order:

1. Red golden: `add_declaration` of a method with `for (int n : items())`,
   `items()` undefined. Expected: the stub is `List<Integer> items()`.
2. In the enhanced-for branch of `Stubs.resultType`, box a primitive loop
   variable's type before it is written. Boxing is a small map from the
   primitive's name to its wrapper, in `Stubs`.
3. The one small edit that changes behaviour: that boxing step.

Hypothesis, to confirm in step 1: the branch writes `List<int>` as the
element type, and the gate refuses it as not a type argument.

## Notes

- `plan:` the hypothesis was wrong about the message. Red, the golden was
  refused, not with the compile error the friction entry gives but "content
  does not parse as members of a type". Turning the boxing off again gave the
  same refusal, so `List<int>` itself, written as the stub's text, is what
  does not parse. The plan's fix holds; the cause is the text, not the type
  check.
- `hand:` the boxing helper (`Stubs.wrapper`) and its use were written by Edit.

## Measured

Golden `stub_enhanced_for_primitive`: red before (refused, as above), green
after (the stub is `List<Integer> items()`). `./mvnw verify` passes; spotless
clean. The session-log hook was not measured for this session.

## Assessment

- **Through the tools or around them:** the change is in `Stubs`, which the
  tools do not edit; the golden went through `add_declaration`.
- **Did the plan hold?** The fix did; the stated cause did not. Recorded
  above rather than corrected in the plan.
- **Reading:** `Stubs.resultType`'s enhanced-for branch, and the refusal path
  that reports the stub's text.

## Follow-ups

- A refusal from a stub's own text ("content does not parse as members of a
  type") names the caller's content, not the stub that was declared. The
  wording points at the wrong place. Not done; a refusal change needs a golden
  of its own.
