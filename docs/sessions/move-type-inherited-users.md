# move_type and the files that inherit the moved type's name

- **Item:** roadmap #65 (local session), found by the stress test on
  Commons Math (`docs/sessions/stress-weighted-targets.md`): moving
  `BSPTreeVisitor.Order` left `OutlineExtractor`, in another package,
  writing `Order` unqualified, which it reached by implementing
  `BSPTreeVisitor`. The user asked (2026-11-04) to go ahead without
  questions, taking the obvious choice.
- **Date:** 2026-11-04
- **Branch:** move-type-inherited-users

## Plan

The change, at the highest level: a file in another package that names the
moved type without a qualifier, through a type it inherits, imports the
moved type; and a member type of an interface, public there without saying
so, is written `public` once top-level.

1. Red: golden `move_type_inherited_users`: `Visitor.Order` (an enum in an
   interface), `other/Impl implements Visitor` writing `Order`. Out:
   `public enum Order`, and `import com.example.Order;` in `Impl`.
2. Green: `MoveType.references` records unqualified names that resolve to
   the moved type; such a file in another package gets the import
   (`Imports.adding`, in order among its imports). An interface's member
   type without an access modifier gets `public`.

## Notes

- test: red as planned (refused: `Order cannot be resolved` in `Impl`).
- plan: `Imports.adding` was new: `Imports.fix` cannot see the name is
  missing, since before the move is written the nested type still resolves
  through the interface.
- found: Commons Math stress seed 7: `BSPTreeVisitor.Order` moves
  (move_type applied 2, was 1); no failures; the copy clean.

## Measured

- tool calls: 8
- through jrefactor: 0 refactoring steps, 12 edit steps
- refused by jrefactor: 0
- by hand: 1
  - Bash: git checkout -q main && git pull -q --ff-only && git checkout -q -b move-type-inherited-users && cat...
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 0
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: python3 - <<'EOF'
- reads: Bash java 1

## Assessment

- **Through the tools or around them:** all through java-refactor-mcp.
- **Did the plan hold?** Yes.

## Follow-ups

- None.
