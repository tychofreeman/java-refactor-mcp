# move of a static method holding an anonymous class (issue #660)

- **Item:** issue #660, roadmap 515, the owner's second of three move/rename bugs
- **Date:** 2026-11-15
- **Branch:** move-static-anonymous

## Plan

The refusal guards an instance method's `this` becoming `receiver`; a static method has none.
Make the two visits in `checkBody` refuse only when `instance`. `movedEdits` already walks into
nested classes: a field or method of the old class read from the anonymous class gets its
qualifier, and the class's own members are not `reached`. `NameCheck` already takes the method
whole.

## Notes

- read: `MoveMember.checkBody`, `movedEdits` (read for #667 in the same session).
- plan: goldens red first (both refused with the old wording), probe red (refused).
- refusal: the probe then failed on the gate: the anonymous class's supertype was a member type of `Main`, #667's bug. Waited for #668 to merge, merged main in, the probe passed.
- Java written through: one `batch` of two `replace_text` steps (the two visits, a comment), one `replace_text` (the class's doc comment). No hand edit of Java.

## Measured

Not measured (no session-log hook here); 2 tool calls that wrote Java, none refused.

## Assessment

Through the tools; the change was two conditions.

## Follow-ups

None.
