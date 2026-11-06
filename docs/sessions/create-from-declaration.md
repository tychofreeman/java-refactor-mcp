# create_file with declaration

- **Item:** roadmap #114, the first part of the constructive-transformations slice
  (`docs/design.md`, "Constructive transformations"; the user, 2026-11-05).
- **Date:** 2026-11-05
- **Branch:** create-from-declaration

## Plan

A Java file can start from its type's declaration rather than its text: `create_file`
with `declaration` (`final class Cart implements Priced`) writes the package line and
the type with an empty body, plus a stub for each abstract method its supertypes leave
unimplemented, typed from the supertype as written (`Comparable<Cart>` gives
`compareTo(Cart ...)`).

Derivation:

1. Goldens first (red): a class implementing an interface (an abstract method stubbed,
   a default one not), a record with no supertype, a generic supertype from the JDK
   (parameter names that binaries lack), and refusals: a body in the declaration, a
   name that is not the file's, both `content` and `declaration`.
2. A new class `Skeleton` (`text(project, path, declaration)`: the header text, parsed
   with bindings, then the stubs of `unimplemented(type)`), by `create_file`; the
   server is the build from the session's start, so `declaration` cannot write itself.
3. `create_file`'s `Def`: `content` no longer required, `declaration` added, its
   description; `Edits.createFile` takes `Skeleton.text` when `declaration` is given
   (`replace_text`), with the one-of-two precondition.
4. Docs: `docs/tool-contract.md` (the argument), the roadmap row, the session notes.

The file's path stays the `file` argument, as for content: the design note's "path
follows from the package and the name" is left for when #113's root choice is built.

## Notes

- read: `Edits.createFile` and the `create_file` `Def` (to place the branch);
  `Stubs`' body text and `Extract.typeName` (to reuse a type namer); `javap` of
  `IMethodBinding` for `getParameterNames` and `isSubsignature`.
- hand: six goldens' fixtures by heredoc (under `resources/`).
- bulk: `Skeleton` (163 lines) by `create_file` with whole content: new behavior, and
  the very loophole this slice addresses. The server is the build from the session's
  start, so `declaration` was not there to write its own skeleton; with it, the class
  would have been a declaration, then `add_declaration` per member.
- refusal: `create_file` of `Skeleton` refused for `IMethodBinding.isDefaultMethod()`,
  which does not exist; resent whole with `Modifier.isDefault` (163 lines twice).

## Measured

`bin/session-report` for the branch: 17 tool calls; through jrefactor 0 refactoring and
4 edit steps; 9 `bin/jrefactor` calls in Bash (reads); refused 1; by hand 2 (this record,
the fixtures); large writes 3 (fixtures 54 lines, `Skeleton` 163, the `createFile`
branch 23); denied 1.

## Assessment

- **Through the tools or around them:** all server code through the tools, but `Skeleton`
  arrived as one 163-line `create_file`, and a refusal for one wrong method name cost
  sending it again. That is the case for the slice: a skeleton, then members one by one,
  would have made the refusal cost one member.
- **Did the plan hold?** Yes; the path stayed the `file` argument, as the plan said.
- **One change that would have helped:** this tool itself; and a `create_file` refusal
  that offered a batch with the fix as a later step (roadmap #110).

## Follow-ups

- Roadmap #115 (a test from its target) is next; #113 (stub classes from tests in the
  main root) would let `declaration` choose the root too.
