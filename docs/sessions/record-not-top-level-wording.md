# A record named where a class is wanted: the refusal's wording

- **Item:** roadmap order 148 (#155), found by `docs/sessions/generics-sketch.md` ("Slice 2 in
  detail"): `Opened.topLevel` and `MemberMove.typeNamed` find classes and interfaces only, so
  a record is refused as "not a top-level type", which it is.
- **Date:** 2026-11-13
- **Branch:** record-not-top-level-wording

## Plan

Written before reading the callers.

The change: the refusal says what the name is (a record, an enum, an annotation) and what to
send. Nothing a tool accepts widens.

1. Red: a golden, a record named to a tool that goes through `Opened.topLevel`
   (`extract_supertype_refused_record`), and the unit test in `OpenedTest` beside the one
   that pins today's wording.
2. Green: in `Opened.topLevel`, when no class or interface has the name, look for another
   top-level declaration of that name and refuse with its kind.
3. Callers of `MemberMove.typeNamed` that refuse with their own "not a top-level" wording:
   read each; change one only if a record reaches it with the wrong words.

## Notes

- read: callers of `Opened.topLevel` by `pattern` (12 tools); `OpenedTest` and `Opened` by lines.
- Red: golden `extract_supertype_refused_record` and `OpenedTest.namesTheKindOfATypeThatIsNotAClassOrInterface`
  (a record and an enum beside a class); both failed on the old wording.
- `Opened` does not know the calling tool, so the refusal says "this tool"; the golden was
  rewritten to that before the code (default, to reopen if a tool name is wanted: pass it in).
- One batch: `replace_text` in `topLevel`, `add_declaration` of `notAClass`; imports by the
  tool. The first version dropped the null check before `resolveBinding()` (a name that
  matches nothing would have thrown a NullPointerException); one `replace_text` put it back
  before the tests ran.
- Not changed: the other callers of `MemberMove.typeNamed`, which refuse in their own words
  (`PullUp`, `MoveMember`, `AddResult`, `InlineClass`, `SubclassToStrategy`). The item asked
  for the wording through `Opened.topLevel`, and nothing a tool accepts widened.
- hand: none in `src/main/java` or `src/test/java`.

## Measured

Not measured: the session-log hook is not loaded in this container. 3 tool calls on Java
(one batch of two steps, one `add_declaration`, one `replace_text`); no hand edit of Java.

## Assessment

Through the tools. The plan held; step 3 was read and left alone.

## Follow-ups

- The same wording for the other `MemberMove.typeNamed` callers, if a refusal there is met.
