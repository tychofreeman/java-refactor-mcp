# A stub after a deleted method that a subclass inherits

- **Item:** the follow-up of `stub-after-delete.md` (#66): a method deleted
  from one nested class and still called from a sibling was not covered by the
  enclosing-type fix. Chosen by the user from `what's next?`, 2026-11-04 (all
  remaining candidates, in order: this, then #22, then #39).
- **Date:** 2026-11-04
- **Branch:** stub-after-delete-inherited

## Plan

The change, at the highest level: a stub must not be declared for a method
that an edit removed from a supertype of the type the call is in.

Operations, in order:

1. Reproduce. The follow-up's sibling case cannot happen as written: an
   unqualified call cannot reach a sibling's instance method. The real shape
   is inheritance: `Child extends Base`, `Base.g()` deleted, `Child.f()` calls
   `g()` unqualified and inherits it. Red golden: the deletion is refused.
2. The removal check (`Stubs.removedAround`) looks at the call's type and its
   enclosing types only. Extend it to the supertypes of those types, looking
   each up in its own file (`sourceOf`), the way the enclosing types are.
3. The one small edit that changes behaviour: that extension in
   `removedAround`.

Hypothesis, to confirm in step 1: `Child` gets a stub `g()`, which compiles
and quietly takes the call from `Base.g`, so the deletion is not refused.

## Notes

- `read:` `Stubs.java` lines 180-211 and `sourceOf`, to find which file
  `removedAround` reads for the owner and the enclosing types. Answer: the
  owner's file for all of them, so an enclosing type in `Child.java` was
  checked in `Child.java` only.
- Reproduced (red): `GoldenTest` run with the golden `stub_after_delete_inherited`
  failed with "Expected Refusal to be thrown, but nothing was thrown". So the
  deletion went through and a stub was declared on `Child`. The sibling case
  from the follow-up was not reproduced; the inherited case is the real one.
- `hand:` the golden's files were written by a shell heredoc (`printf`), not
  `create_file`, which would have fit (`create_file` for `in/` and
  `args.properties`). The tool server's tools are not loaded in this session
  (no `mcp__jrefactor__*` in the tool list), so `bin/jrefactor` was the
  alternative; not tried.
- `hand:` the fix in `Stubs.java`: `removedAround` gained a supertype check, and
  `removedInSupertypes` (new, in `Stubs.Round`) was added after it, both by Edit.
  The tools that fit: `replace_declaration` for `removedAround`, and
  `add_declaration` (type `Stubs.Round`) for `removedInSupertypes`. Not tried, for the
  reason above.
- `plan:` none. The three operations held: reproduce, extend the check, one edit.
- `refusal:` none.

## Measured

Not measured: no session log for this branch (`bin/session-report` found none;
the hook did not load for this session). Counted from the notes: 1 Java file
edited by hand (`Stubs.java`), 1 golden written by a shell heredoc, 0 refusals
from the tools, 1 read of ~30 lines of `Stubs.java`.

## Assessment

- **Through the tools or around them:** around. The fix and the golden were
  written by hand, though the tools for both exist (`replace_declaration`,
  `add_declaration`, `create_file`). The reason in the notes is that the
  server's tools were not loaded in this session; that is a session gap, not a
  tool gap. Next session on this item should load them first.
- **Large writes:** none. The new method is about 20 lines of new behaviour.
- **Did the plan hold?** Yes: the reproduction came first and the golden went
  red for the reason the plan named. The sibling premise did not hold, and the
  plan said so.
- **Reading:** `removedAround` and `sourceOf` only, for the question above.
- **One change that would have helped:** the test harness could report which
  tool the session has loaded, so a session starts with the edit tools in hand.
  Not a roadmap item yet; recorded here.

## Follow-ups

- The sibling nested class (a deleted member called from a sibling nested
  class): still not reproduced; `stub-after-delete.md` has it open.
- `removedAround` reads the owner's file for the enclosing types too (`target`).
  Correct when the call and the owner share a file; wrong when an enclosing
  type is in another file than the owner. Not reproduced; a golden would need
  a call whose owner is inherited from another file.
- A qualified call (`child.g()`) to a method deleted from a supertype is now
  refused through the owner check. No golden covers that yet.
- The first `verify` run printed Maven's "malformed POM" error. That text is
  `SourceProjectTest`'s intended bad `pom.xml` (the failed-classpath case),
  printed by Maven; a second run passed with no failures. Not traced further.
- The reproducing golden is `stub_after_delete_inherited`.
