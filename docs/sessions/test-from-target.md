# A test from its target

- **Item:** roadmap #115, the second part of the constructive-transformations slice
  (`docs/design.md`, "Constructive transformations").
- **Date:** 2026-11-05
- **Branch:** test-from-target

## Plan

A test starts from what it tests, not from its text: `test_for` names the target
(`Cart.total`, `Cart.add(int)` for one overload), and the tool writes the test method's
shell: the receiver built with its fewest-argument constructor (placeholder arguments:
`0`, `false`, `""`, `null`), the call, the result in a local named after the method, and
`fail("write the assertion")`, so the test is red until the model types the assertion.
`create_file` with `test_for` writes the test file with that method (or empty, for a type);
`add_declaration` with `test_for` adds one to an existing test class. The framework is
JUnit Jupiter when the classpath has it, else JUnit 4, else refused.

Derivation:

1. Goldens first (red), with a `classpath=junit` option in `GoldenTest` that gives a case
   the JUnit jars of the test JVM (`replace_text` of `runCase`, and `call` skipping the key).
2. `TestFor` by `bin/jrefactor create_file declaration=...` (the CLI's jar has #114; the
   MCP server is the session's start build), then its members by `add_declaration`: the
   target lookup through `Holding`, the method text, the framework from `Imports`'
   index (a small `Imports.known`).
3. `create_file` and `add_declaration` take `test_for` (`Def` arguments, the branch in
   `Edits.createFile` and in `Declarations`' add), with the one-of refusal widened.
4. Docs: contract, descriptions, roadmap, session notes.

## Notes

- read: `SourceProject` and `MavenBuild.sourceRoots` (no named test root: a golden has
  one root, so `file` stays required); `Inspect.names` and `chain` (how a name is found
  anywhere); `GoldenTest.runCase` and `call`; `Declarations.add` and `Edits.createFile`.
- hand: five goldens' fixtures by heredoc (under `resources/`).
- `TestFor` began as `bin/jrefactor create_file declaration='final class TestFor'` (three
  lines), the slice's first use of #114.
- bulk: the members of `TestFor` (204 lines) in one `add_declaration`: new behavior. The
  constructive tools would have made the type's shell; the members are logic, typed.
  Members one call each would have cost 13 calls for no less text.
- bulk: the `test_for` branches of `Edits.createFile` (33 lines) and `Declarations.add`
  (47) by `replace_text`: about half is the old lines carried in `old` and `new`.
- refusal: a `replace_text` whose `old` was stale (Spotless had joined two lines since
  #213); resent from `inspect` `pattern`.
- refusal: a `replace_text` with `old` two spaces too deep (the separator in `inspect`'s
  output counted as indentation, the session notes' trap); resent.
- plan: the mirrored test path was dropped: the test root has no name in
  `SourceProject`, so `file` is required as for `declaration`.

## Measured

`bin/session-report` for the branch: 26 tool calls; through jrefactor 0 refactoring and
15 edit steps; 8 `bin/jrefactor` calls in Bash (reads, and the `declaration` create);
refused 2, each resent through the tools; by hand 2 (this record, the fixtures); large
writes 4 (fixtures 80 lines, `TestFor`'s members 204, the two branches 33 and 47).

## Assessment

- **Through the tools or around them:** all server code through the tools; the type began
  from its declaration. The members were typed in one call, as logic is.
- **Large writes:** the 204 lines are new behavior. The two `replace_text` branches carried
  their old lines as `old`, where `insert_statements` before a line would have sent only
  the new ones.
- **Did the plan hold?** Yes, except the mirrored path.
- **One change that would have helped:** `replace_text`'s indentation trap again (roadmap
  #106, now with a fourth case).

## Follow-ups

- Roadmap #116 (skeleton-only creation, a flag) is next.
- A mirrored test path (`file` optional with `test_for`) needs the test root named in
  `SourceProject`; with #113.
