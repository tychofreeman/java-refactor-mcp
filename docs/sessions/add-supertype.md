# add_supertype (the fifth of the ten higher-level tools)

**Item:** add a supertype to a top-level class or interface: `extends` or
`implements`, written into its header.
**Source:** the composite comparison of 2026-11-04 (Extract Interface, Extract
Superclass), option B, after the four tools built so far (the user's "yes,
continue with add_supertype", 2026-11-04).
**Date:** 2026-11-04

## Plan

Change at the highest level: a type is named with the supertype as written and
the relation (`extends` or `implements`). The tool writes the clause into the
header, and the gate checks that the supertype resolves and that the type
still compiles.

Operations, in order:
1. Find the top-level type in the file. Refuse a relation other than `extends`
   or `implements`.
2. For a class, refuse `extends` when it already has a superclass. For an
   interface, `extends` adds to its list; `implements` is refused.
3. Insert the clause before the body's `{`: `extends X `, or `implements X `
   when there is no list yet, or `, X` after the last name in an existing list.
4. Apply the edit through `Edits.gated`: the gate refuses a supertype that does
   not resolve (it stays a refusal, with no stubs, since this is a refactoring).

Arguments: `file`, `name` (the top-level type), `relation` (`extends` or
`implements`), `supertype` (as written).
Not in v1: moving methods into the new supertype (`pull_up` does that),
generic supertypes' type arguments beyond what is written, and removing a supertype.

## Golden cases (written first)

- `add_supertype_implements`: `public class Price` gains `implements Priced`.
- `add_supertype_extends`: `public class Price` gains `extends Base`.
- `add_supertype_refused_already_extends`: a class with `extends Base` gains
  `extends Other`; refused, "already extends".
- `add_supertype_refused_unknown`: `implements Missing`, which does not exist;
  refused, "would add compile errors".

## Notes

- `hand:` the four golden cases' `in/` and `out/` files, written with `Write` before the tool
  existed (the tool is the thing being built, so no tool could write them).
- `hand:` `AddSupertype.java`, about 100 lines, written with a heredoc. It is new behavior
  (header clauses by the AST and the text offsets), not existing code typed again. No tool writes
  a new tool's body; `create_file` would have written the file, and the gate then checks it.
- `hand:` the `ADD_SUPERTYPE` `Def` and its `ALL` entry, and `addSupertype` in `Tools`, written
  with a Python script that checks each anchor occurs once. An `add_declaration` of the `Def` and
  a `replace_text` of the `ALL` list would have been the tools; both were built only by this PR.
- `plan:` the plan named insertion before `{`. Two cases came from the goldens' shape, not the
  plan: an interface's `extends` joins its existing list after the last name, and a class's
  `extends` goes before an existing `implements` keyword (found by `lastIndexOf` from the first
  interface), since the superclass comes first in the header.

## Measured

Not measured: `bin/session-report` reports no session log for this branch (the hook was not
loaded when the session started). Counts are from the notes above: 0 refusals hit while writing,
1 full verify run before the PR (228 tests, 0 failures, 4 skipped).

## Assessment

- **Through the tools or around them:** the tool itself could not be used on its own
  construction, so its class, registration and goldens were written by hand, as the first four
  of the ten were. After the server runs this change, its own body is the only part that is not a
  tool's output.
- **Large writes:** `AddSupertype.java` is new behavior. The registration is new text in a list,
  which no tool in the current set adds.
- **Did the plan hold?** Operations 1-4 were used as planned. Operation 3 needed the two cases
  above, which the plan did not name: an interface's list, and a class's `extends` before an
  existing `implements` clause. Both are in the notes and the class comment.
- **Reading:** none beyond `ChangeType` and `Tools` (the registration points), read to copy
  their shape.
- **One change that would have helped:** a tool that adds a supertype's `implements` to an
  existing list without the caller stating the kind of the supertype. Not built: the user chose
  stating the relation explicitly (the plan's Follow-ups).

## Follow-ups

- Done in this PR: the tool, goldens, docs, full verify.

- Choose the relation from the supertype's kind, so the caller does not state it.
- Remove a supertype; move the members an extraction needs (with `pull_up`).
