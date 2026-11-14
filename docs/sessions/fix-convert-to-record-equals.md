# convert_to_record: equals overload, annotations, constructor access (issue #476)

- **Item:** issue #476 from the 2026-11-13 code review (roadmap row 224, order 216)
- **Date:** 2026-11-13
- **Branch:** fix-convert-to-record-equals

## Plan

Four small changes in `ConvertToRecord`, each a refusal or a carried token: `equals` counts only
with an `Object` parameter (by binding); a field's annotations prefix its component; a transient
field and a constructor less visible than the class are refused, with the alternatives recorded.

## Notes

- read: `ConvertToRecord` lines 1-260 by `inspect`; `MemberMove.removal`, `TextEdit` by `inspect`.
- red: probe `convert_to_record_equals_overload` printed false then true (the record's generated
  `equals(Object)`); goldens for the overload, transient and private constructor refusals applied
  or failed; `convert_to_record_annotations` dropped the annotations.
- found: a class with only its fields and constructor wrote code that did not parse: the
  constructor, last, takes the blank line before it, which the last field's removal took too, and
  the overlapping edits broke the text. `remove` clips the later removal. The annotations golden
  pins it.
- plan: the annotations golden first had a package-private constructor in a public class, which
  the new rule refuses; made public.

## Measured

Not measured: the session-log hook is not loaded here.

## Assessment

Through the tools. The plan held, plus the overlap fix.

## Follow-ups

- `refuseSubclasses` still sees only named classes; an anonymous subclass gets the gate's error.
- The result does not say where carried annotations also land (accessor, parameter).
