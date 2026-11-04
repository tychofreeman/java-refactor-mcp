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

