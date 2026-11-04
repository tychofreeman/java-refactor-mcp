# pull_up: move a method to a supertype

- **Item:** roadmap order 25 (#17), `pull_up` and `push_down`. The user asked on
  2026-11-04 ("Go ahead and do push_up / pull_down": by the roadmap's names,
  `pull_up` moves a member up, `push_down` moves it down). This record is the
  first of the two, one PR each.
- **Date:** 2026-11-04
- **Branch:** pull-up

## Decisions taken without asking (the obvious defaults)

- Target: the direct superclass by default; a `to` argument names another direct
  supertype (an interface). Several direct supertypes and no `to` is refused with
  the names to choose from.
- Scope of this PR: methods only. Fields are a follow-up in the same row.
- The member must not use anything only the subclass has: the type-check gate
  refuses that, as it does for every edit.
- Overrides: a method in the subclass that overrides the moved one is refused,
  not deleted.

## Plan

The change, at the highest level: a method moves from its class to a direct
supertype, its declaration and every call that the gate can see stay valid.

Operations, in order:
1. Red golden: `pull_up` of a method with an unqualified use, expected output
   with the method in the superclass and the call unchanged.
2. A refusal golden: the method uses a subclass field.
3. Implement: find the declaration, check the target, remove the member from the
   subclass, add it to the supertype (its text, imports fixed), gate once.

The one edit that changes behaviour: none outside the new tool.

## Notes

- `plan:` the record was written before reading the code. Two goldens were then
  written first (`pull_up_method`, `pull_up_refused_subclass_field`) and ran red:
  "No tool pull_up". Then `PullUp.java` and the `Tools` registration.
- `read:` `Tools` (the `move_type` definition and dispatch), `MoveType.run`'s
  opening lines, and the `Lines`, `TextEdit` and `Holding` signatures. Each once.
- `hand:` the two golden cases, `PullUp.java` and the `Tools` registration were
  written by hand (heredoc, and one Python exact-match replace for `Tools`). The
  java-refactor-mcp tools are not in this session's tool list, so `bin/jrefactor`
  was the alternative; not tried. `create_file` and `add_declaration` would have
  made the new files, and `replace_text` the `Tools` edit.
- `bulk:` `PullUp.java` is about 170 lines of new behaviour, all written at once;
  nothing existing was retyped.
- `refusal:` none from the tools.
- `plan:` none changed. The methods-only scope was in the plan.

## Measured

Not measured: no session log for this branch.
Verify: `./mvnw verify` passed, 200 tests, 0 failures, 4 skipped. The golden run
had 134 tests, with the two new cases among them.

## Assessment

- **Through the tools or around them:** around. The new tool, its goldens and its
  registration were all written by hand, because the server's tools were not loaded
  in this session. The tools for each exist, so this is the session gap again.
- **Did the plan hold?** Yes: the goldens were written first and went red for the
  stated reason; the moved method and the refusal both match the plan.
- **Large writes:** `PullUp.java` is new behaviour, not existing code retyped.
- **One change that would have helped:** the java-refactor-mcp tools loaded at the
  start of a session (the same gap as `stubs-test-tree.md`).

## Follow-ups

- Fields: `pull_up` of a field is not built; the row says so.
- Overloads: `pull_up` refuses them. Choosing one by its parameter list would need
  the overload syntax `Declarations` already parses.
- `push_down` is the next PR of order 25 (to every direct subtype).

