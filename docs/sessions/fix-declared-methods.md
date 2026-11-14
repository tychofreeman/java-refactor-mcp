# getDeclaredMethods outside a parseAll batch

- **Item:** issue #431 (the owner said yes, 2026-11-13): a project type that a `Jdt.parseAll`
  batch read from the source path answers no methods once the batch has ended. Roadmap #185.
- **Date:** 2026-11-13
- **Branch:** fix-declared-methods

## Plan

The change: bindings handed out by `parseAll` keep the members of the project types they name.
Operations: a unit test that reproduces the issue's probe (`Use` alone in the batch, `Util` on
the source path), red; then the smallest of the issue's three options, asking the types within
the batch's `acceptAST`, as one helper in `Jdt` called from the callback; no tool changed.
Checked first whether #439 shares the cause: it does not (`Opened.topLevel` takes a
`TypeDeclaration` only), so it is its own PR.

## Notes

- read: `Jdt` through `inspect` and the parse methods by line range; the `getDeclared*` callers by
  grep over `src/main` (40 sites), to judge the options.
- plan: `create_file` of `JdtTest` (red: 0 methods, the log line printed); a second test for
  `Jdt.parse` of one file, which answered 2 (the default constructor and `twice`): only the batch
  is affected, so both tests expect 2 (`replace_text` `all_occurrences`).
- refusal: `batch` with the step arguments beside `tool` ("replace_text needs file"); resent under
  `args`. Then one batch: two `replace_text` steps in `parseAll` and `add_declaration` of the two
  `completeMembers` overloads (imports added). Green.
- plan: refactor while green: the test's duplicated `Util` source through `extract_variable`
  `as=constant`, then `replace_expression` of the second copy.
- `./mvnw -o verify`: green, 779 golden cases; no change in run time worth naming.
- hand: none in Java. Docs through `replace_text`, this record written by hand.

## Measured

Not measured: the session-log hook is not loaded in this session. From the notes: 7 tool calls
to Java (1 `create_file`, 1 `add_declaration`, 2 `replace_text`, 1 `batch` of 3, 1
`extract_variable`, 1 `replace_expression`), 1 refusal, no hand edit to Java.

## Assessment

Through the tools. The plan held. The fix is a default to reopen: it completes the types a unit's
names bind to and those around, above and inside them, not every type a binding can reach.

## Follow-ups

- A type reached only through another binding (a parameter or return type of a method of a
  source-path type) is still incomplete after the batch; if a tool needs one, parse its file
  with the batch or read it inside the callback (session notes, Traps).
