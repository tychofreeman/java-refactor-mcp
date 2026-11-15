# `move` of a member type into another class (slice 1)

- **Item:** roadmap #513, slice 1: `move name=Outer.Inner to=Target` for a static member type into
  a top-level class of the same package. The owner chose option (A) of
  `docs/sessions/member-type-move-sketch.md` and its recommendations (inner classes refused,
  references qualified).
- **Date:** 2026-11-15
- **Branch:** member-type-to

## Plan

A member type moving into another class is `MoveType`'s detach half (qualify what the type reached
through its outer class, widen privates across the boundary, accessor calls, permits) plus an
attach half: references written relative to the target, placement by kind, the target's imports.
Make the change easy first: `MovedType` learns where the type goes (`into`, with `once()` for the
refusal wording and `movedName` for `permits`), and `MoveType`'s helpers become package-private.
Then goldens (red), then `NestType` composing them, then the router (`Tools.move`: `to` with a
member type goes to `move_type`).

## Notes

- plan: `add_parameter` on the record `MovedType` added the component and passed null at the one
  construction; `add_declaration` `once`, `replace_declaration` `movedName`, `replace_text` for the
  refusal wording and four visibility changes, in two batches.
- red: 12 goldens (`move_type_into*`) and the probe `move_type_into`, written as resource files;
  the 12 failed before `NestType` (routed to the member route's refusal).
- plan: `NestType` written as a new file through `create_file` (`content_file`); the router and
  `move_type`'s `to` argument through one batch (`replace_text` three times, `add_declaration` of
  `memberType`, whose stub the replace step declared first).
- refusal: none from the tools on this branch. The batch's section names must be quoted as
  `"@name"` in the JSON (sent `"@@name"` once: refused, nothing changed).
- read: `MoveType`, `MovedType`, `Tools.move`, `MemberMove.addTo`/`removal`, `Edits.gatedFiles`,
  `NameCheck` through `inspect` (`names` and line ranges).
- golden: two expectations (enum and record) had a blank line left after the removed member, as
  `move` to its own file leaves; `MemberMove.removal` takes it, so the goldens were corrected.
- description: `move` was at 1,976 characters (its ratchet); the new route's words were paid for by
  trimming the others, to 1,964, and the ratchet lowered to it. It is still over the 1,700 limit.

## Measured

Not measured: the session-log hook is not loaded here. From the notes: Java changes through the
tools 100% (every edit to `src/main/java` and `src/test/java` went through `bin/jrefactor`); by
hand only resource files (goldens, probe) and docs.

## Assessment

Through the tools. The plan held: `MoveType`'s helpers took a `MovedType` already (#528), so the
detach half was reused unchanged but for one refusal's wording. The new code is the reference walk
(about 70 lines), the target's checks and the imports.

## Follow-ups

- Slice 2 of this branch's series: move `Signatures`' records to `MethodFamily` with the tool
  (#528's last leftover).
- Left for the owner (roadmap #513 stays open): a target in another package, an interface target,
  a target in the outer type's own file or a member type as target, an inner class without
  `this` uses.
