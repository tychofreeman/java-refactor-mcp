# type_switch_to_method: a member enum sees its enclosing class's statics

- **Item:** issue #436, from the Java 17 stress run (`docs/sessions/stress-enum-switch.md`):
  `case CLASS -> CLASS_FLAGS;` over a member enum `Flag.Location` was refused as a static of `Flag`
  that `Location` "does not see by that name". Roadmap #183.
- **Date:** 2026-11-13
- **Branch:** fix-enum-outer-static

## Plan

`EnumSwitch.checkStatics` lets through only a static the enum itself declares. Ask instead
whether the enum sees it by its simple name: walk from the enum outward through its enclosing
types; reaching the static's class means it is seen, unless a type passed first declares a member
of that name (a constant `ARRAY` hiding `Path.ARRAY`), which keeps the refusal and its qualify
fix. `NameCheck` already runs on the moved bodies, so what the walk does not cover (a name an
enum inherits) is still judged there. Goldens first: the issue's case applying, and a hidden
static refused with the same wording.

## Notes

- read: `inspect` of `EnumSwitch.checkStatics` (read whole in #435's session), `NameCheck.require`
  by `names`, its refusal by `pattern`.
- plan: two goldens; the applying one failed with the issue's refusal; the hidden one passed
  already (it pins the refusal).
- refusal: the first `batch` sent each step's arguments beside `tool`, not in `args`; "replace_text
  needs file". Resent nested; my mistake, not the tool's.
- One `batch`: `replace_text` of the condition and of the doc comment, `add_declaration` of
  `sees` (an `@@sees` section); checked at the end, `IBinding` imported.
- Goldens passed on the first run.
- Default to reopen: a static the enclosing class inherits (from its superclass or interfaces) is
  still refused, over walking each enclosing type's supertypes; no stress call met it.

## Measured

Not measured: the session-log hook is not loaded in this session. One batch of three tool steps,
no hand edits of Java.

## Assessment

Through the tools. The plan held; the wording of the refusal did not need to change, as it is
now said only where the enum does not see the name.

## Follow-ups

- Statics an enclosing class inherits, if a task meets them.
