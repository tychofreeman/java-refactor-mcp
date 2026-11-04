# move_type qualifies what the outer type inherits

- **Item:** the third gap from `docs/sessions/stress-refactorings.md`
  (#70): `move_type` of Commons Lang's `AtomicInitializer.Builder` was
  refused by the gate. The class uses `AbstractBuilder`, a member type
  `AtomicInitializer` inherits from its supertype, and `MoveType` qualifies
  only the outer type's own members. The user asked (2026-11-03) for the
  three gaps; this is the last.
- **Date:** 2026-11-03
- **Branch:** move-type-inherited

## Plan

The change, at the highest level: inside the moved type, an unqualified
name that reaches a static member or member type through the outer type,
its own or one it inherits, is qualified by the type that declares it, and
the moved file keeps that type's import.

Derivation, in order:

1. Red: golden `move_type_inherited`: `Outer extends Base` (another
   package); `Outer.Inner` uses `Helper` (a member type of `Base`) and
   `util()` (a static method of `Base`) unqualified. Out: `Inner.java` with
   `Base.Helper` and `Base.util()`, and `import com.example.base.Base`.
2. Green: `MoveType.memberOf` accepts a member declared by the outer type
   or any of its supertypes (not one the moved type reaches through its own
   supertypes, which needs no qualifier), and the qualifier is the
   declaring type's label (`Names.typeLabel`), not always the outer one's.
3. Green: a qualifier's top-level name counts as used inside the moved
   text, so its import is kept.
4. Rerun the Commons Lang stress seed that found it.

## Notes

- test: red, worse than expected: the move applied. The stubs declared an
  empty `com/example/Helper.java` for the unqualified `Helper`, and the gate
  passed it. On Commons Lang other errors had made the gate refuse.
- refusal: the code batch, step 5: `old` at the wrong indentation (10
  spaces, not 12). Resent.
- plan: `memberOf` became `qualifier`, which returns the qualifier: the
  outer type's label for its own members, `Names.typeLabel` of the
  declaring type for what the outer type or a type enclosing it inherits;
  none for what the moved type reaches through its own supertypes.
- found: Commons Lang stress seed 1: `AtomicInitializer.Builder` moves
  (move_type applied 4, was 3), no refusal by the gate, no failures; the
  copy matched a fresh unpack.

## Measured

`bin/session-report` (`.claude/session-log/move-type-inherited.jsonl`):

- tool calls: 11
- through jrefactor: 0 refactoring steps, 13 edit steps
- refused by jrefactor: 1
- by hand: 1
  - Bash: git checkout -q -b move-type-inherited && grep -n 'static String typeLabel' -A12 src/main/java/io/gi...
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 1
  - replace_declaration src/main/java/io/github/tychofreeman/jrefactor/MoveType.java: 54 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: R=$(bin/session-report 2>/dev/null | tail -30); cat >> docs/sessions/move-type-inherited.md <<EOF
- reads: Bash java 2, inspect 1

## Assessment

- **Through the tools or around them:** all Java and fixtures through
  java-refactor-mcp; by hand: the docs and this record.
- **Did the plan hold?** Yes; steps 2 and 3 were one batch.
- **What went badly:** the case showed that stubs can hide a wrong move:
  an unqualified name that resolves to nothing became a new empty class,
  and the gate saw no new error.
- **One change that would have helped:** stubs not declared for a
  refactoring's own output (a refactoring changes no behavior, so it needs
  none). Friction log.

## Follow-ups

- Stubs during a refactoring: a refactoring should not need any; declaring
  one hides its mistake (friction log, 2026-11-03).
- A static import in the outer file (`import static a.B.m`) used inside the
  moved type is kept, since its name is used inside; not tested.
