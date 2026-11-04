# pull_up and push_down for overloads

- **Item:** roadmap order 25 (#17), the overload half: a method with overloads is
  refused by `pull_up` and `push_down` today (`docs/sessions/fields-pull-push.md`
  left it open). Chosen by the user's standing order to continue with the open items.
- **Date:** 2026-11-04
- **Branch:** overloads-pull-push

## Decisions taken without asking (the obvious defaults)

- The name selects an overload the way `replace_declaration` does: `Sub.hello(int)`,
  with the parameter types as written (`Map<String, Integer>` works). The same
  `Declarations` parsing is used, not a second one.
- A name without a parameter list that matches several overloads is refused, and the
  refusal lists them as `hello(int), hello(String)` so the model can pick one.
- `pull_up`'s "already declares" check compares signatures, not names: moving
  `hello(int)` up is refused only if the supertype declares `hello(int)`.
- `push_down` of an overload is refused when a subclass declares the same signature.
- The override refusal for `push_down` stays: an overload with the same signature as
  a superclass method is an override.

## Plan

Operations, in order:
1. Red goldens: `pull_up_overload` (`Sub.hello(int)` moves; `hello(String)` stays),
   `pull_up_overload_ambiguous` (no parameter list, refused and listing both),
   `push_down_overload` (`Sub.hello(int)` moves down).
2. Make `MemberMove.member` read a parameter list and choose the overload; refuse the
   ambiguous name with the list.
3. Compare signatures in `MemberMove.declares` for methods, so a same-named overload in
   the target is not refused.
4. Refactor only what the change needs; keep the goldens green.

The one edit that changes behaviour: a name with a parameter list is now accepted by
`pull_up` and `push_down`; before, it was refused as an overload.

## Notes

- `plan:` the record was written before reading the code. The three goldens went
  red ("No tool" for the bare errors, and the ambiguous one lacked the listing).
- `hand:` `Declarations.types` was made package-private, so `MemberMove` could use
  it. The goldens, `MemberMove`, `PullUp`, `PushDown` and the `Tools` descriptions
  were written by hand (a heredoc, and Python replaces), as in the earlier items.
- `refactor:` `declares` now compares signatures by binding (`erasedParams`), not by
  name, in both tools, so an overload with other parameters is not "already declares".
- `read:` `Declarations.find` (lines 270-305), to reuse its parameter-list parsing.
- `refusal:` none from the tools.

## Measured

Not measured: no session log for this branch.
Verify: `./mvnw verify` passed, 210 tests, 0 failures, 4 skipped.

## Assessment

- **Through the tools or around them:** around, as in the earlier items: the
  java-refactor-mcp tools were not loaded in this session.
- **Did the plan hold?** Yes. The goldens went red for the stated reasons. The
  signature comparison went one step further than the plan: the name-only check
  was replaced, not just joined by a parameter list.

## Follow-ups

- A subclass nested in another type is still not found by `push_down` (top-level
  subclasses only).
