# One shape for a refactoring (roadmap #71)

- **Item:** roadmap #71: a shared target resolver and preconditions as
  values, before #72 (one effects analysis) and #73 (`preserve_exceptions`).
  The user asked for all three in succession (2026-11-04), and then, being
  away, for the decisions to be made here and reported.
- **Date:** 2026-11-04
- **Branch:** refactoring-shape

## Decisions (made by this session; the user was away)

- **Scope.** The resolver takes the preamble every tool repeats (read,
  parse, find the top-level type, split `Type.member`, find the node that
  starts on a line), and `Precondition` holds the checks a tool makes before
  it plans an edit. Refusals in the middle of an algorithm (a call site
  that cannot be rewritten, an overload found while walking the project)
  stay `throw`: they depend on state the algorithm builds, and #73's flag
  needs the up-front checks, not those. Not chosen: every refusal a value
  (rewrites each algorithm, for no caller that needs it); the resolver only
  (leaves #73 nowhere to attach a "report, do not refuse" check).
- **Shape.** `Opened` (a record: project, `rel`, file, text, unit) with
  `of(project, rel)`, `topLevel(name)` and `startingOn(line, ...)`;
  `Qualified.of(name, shape)` for `Type.member`; `Precondition` (a lazy
  `holds` and a lazy refusal) with `require(...)`, which checks in order, so
  a later check may assume the earlier ones held. Lazy, so a check is a value
  that #73 can mark to report instead of refuse.
- **Wording.** One wording for each resolver refusal: "X is not a top-level
  type of `rel` that resolves." (was "class", "class or interface", with or
  without "Fix it first."). No golden pinned the old ones; a new golden pins
  the new one. Refusal wording is contract, so the description changes are
  none (no tool's description quotes these).
- **Split at the first dot.** `Qualified` splits `Type.member` at the first
  dot, as `MoveMember` and `EncapsulateField` did. `PullUp`, `PushDown`,
  `InlineFunction` and `ChangeType` split at the last, which takes
  `Sub.m(Map.Entry)` as type `Sub.m(Map` and member `Entry)`: a bug, fixed
  by the change.

## Plan

The change: a tool says what it needs (a file, a top-level type, a member,
a node on a line) and what must hold, and one place resolves and refuses.

1. Green: `create_file` `Opened`, `Qualified`, `Precondition`; unit tests
   for each in `OpenedTest` (`create_file`), written first and red through
   stubs.
2. Green, one tool at a time with the goldens after each: `replace_text` of
   each preamble with `Opened.of`, `Qualified.of`, `topLevel`,
   `startingOn`, and of the up-front `if`/`throw` runs with
   `Precondition.require(...)`. Tools: `ChangeType`, `AddSupertype`,
   `InlineFunction`, `MoveMember`, `EncapsulateField`, `PullUp`, `PushDown`,
   `InvertIf`, `RemoveElse`, `MoveStatement`, `InlineVariable`,
   `ExtractVariable`. `Rename`, `MoveType`, `Extract`, `Declarations` and
   the signature tools keep their own resolvers (`Rename.Query`,
   `Declarations.find`, `MethodFamily.of`) this time: each is larger and
   addressed differently; follow-up.
3. Golden `pull_up_refused_no_type` for the one wording.

## Notes

- `plan:` the three new classes and their test were created in one batch,
  not test first: a test written first would have had its stubs declared in
  the test tree (stubs for a class only a test uses go there), and moving
  them to `src/main` takes a delete and a create. `OpenedTest` passed on
  its first run.
- `refusal:` the batch for `MoveMember` replaced the `@Override` loop, which
  removed the `Annotation` import, then `add_declaration` of `overrides`
  needed it and was refused as ambiguous (five classes). Sent again with
  `imports`. The suggested import was `org.eclipse.jface.text.source`, not
  the `dom` one the file had imported a step earlier.
- `plan:` `MoveMember` now resolves the member before checking `to`, so a
  missing member is refused before a wrong `to`. No golden depends on the
  order.
- The goldens and unit tests passed after each group of tools (three, four,
  five), with no change to an expected result.

## Measured

From `bin/session-report` (before the docs edits):

- tool calls: 17
- through jrefactor: 21 edit steps
- refused by jrefactor: 1 (the ambiguous `Annotation`, above)
- by hand: 0 Java; the record and the docs by Write, `sed`, a heredoc and
  Python
- large writes: 7: the three new classes and their test (new), and the
  `replace_text` of the preambles (existing code written again in its new
  form: each replaces 15-55 lines of `if`/`throw` with a shorter call)
- reads: Bash java 1, plus the `sed` views of the preambles while planning

## Assessment

- **Through the tools or around them:** through. Each preamble was
  rewritten by `replace_text` with the old text as read: no tool turns an
  `if`/`throw` run into a value, and the change is that rewrite.
- **Large writes:** the preamble rewrites are existing checks in a new
  form, which is the item; none could have been moved by a refactoring.
- **Did the plan hold?** Yes, for the twelve tools named; the other five
  stayed out as planned.
- **Reading:** the preambles, by `sed`, to write each replacement.
- **One change that would have helped:** an import suggestion that prefers
  the package the file imports from (already in the friction log,
  2026-11-04); it came up again.

## Follow-ups

- `Rename` (`Query`), `MoveType`, `Extract`, `Declarations` (`find`) and the
  signature tools (`MethodFamily.of`) onto `Opened`.
- Further up-front checks in `InlineVariable`, `ExtractVariable`,
  `ChangeType` and `EncapsulateField` (the field lookup) are still
  `if`/`throw`; convert them when each is next changed.
