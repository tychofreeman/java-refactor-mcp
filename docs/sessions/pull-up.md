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

- `plan:` the record was written before reading the code.

