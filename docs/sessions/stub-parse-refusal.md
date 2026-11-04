# A stub's unparseable text is refused as the caller's content

- **Item:** the follow-up of `stub-enhanced-for-primitive.md` (#69): when a
  stub's own text does not parse, the refusal says "content does not parse as
  members of a type ... Send whole declarations", which blames the caller's
  content, not the stub. Chosen by the user from `what's next?`, 2026-11-04.
- **Date:** 2026-11-04
- **Branch:** stub-parse-refusal

## Plan

The change, at the highest level: a refusal that comes from a stub's text
names the stub, its type and its file, and not the caller's content, so the
model knows which declaration to change.

Operations, in order:

1. Find a stub text that does not parse, by a call the gate can see. If none
   is found, write the refusal path's golden from the parser's message alone
   (a unit test over `Stubs`), and say so in the record.
2. Red golden: an edit whose stub text fails to parse. Expected: a refusal
   that names the stub (`Type.method(params)`) and says it does not parse,
   and nothing changed.
3. In `Stubs.Round.write`, catch the parse refusal from `Declarations.plan`
   and rethrow with the stub's label in the wording.

Hypothesis, to confirm in step 1: the stub writer (`Round.write` →
`Declarations.plan`) is the only path a stub text takes to the parser, so a
refusal raised there can name the stub.

## Notes

- `read:` `paramName` and `Declarations.members`, to find where a stub's text
  is parsed and which names a stub can take. `paramName` already replaces a
  keyword with `value`, so the names cannot fail to parse.
- Probe (red, deleted after): `add_declaration` of three methods calling an
  undefined `store` with `null`, `new int[0]` and `() -> 1`. None reached the
  parser's refusal. The `null` and array stubs were declared; the lambda one
  was refused as a compile error: `store(Object)` cannot take `() -> {}`.
- `plan:` the hypothesis (step 1) does not hold: no call found produces a stub
  text that fails to parse, so the parse refusal cannot be given a golden from
  a call. Step 2 (a red golden for the refusal wording) is not done.
- `hand:` none in the source. The probe golden was written and deleted again.

## Measured

Not measured by the session-log hook. The code is unchanged in this item.

## Assessment

- **Through the tools or around them:** no code changed; the probe went
  through `add_declaration`, which is the tool that would have produced the
  refusal.
- **Did the plan hold?** No. Step 1 failed: the parse path has no reachable
  trigger from a call. The item is closed as not reproducible, not as fixed.
- **Reading:** `paramName`, `Declarations.members`, `Round.write`.
- **New gap found:** a lambda argument to an undefined method is typed
  `Object`, so the stub refuses it (`friction-log.md`, 2026-11-04). This is the
  next item if the user wants it.

## Follow-ups

- The parse refusal's wording still blames the caller's content, if a stub
  text ever fails to parse. Changing it needs a way to produce such a stub;
  none is known.
- Lambda arguments to an undefined method: see the friction entry.
