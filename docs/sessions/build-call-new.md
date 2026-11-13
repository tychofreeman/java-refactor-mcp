# `build` with `call` and `new`

- **Item:** roadmap #149 (order 142), the generators sketch's increment 3: `build` with `call`
  and `new` entries and typed strings on `insert_statements` and `replace_statements`. Chosen
  by the user's review of the sketch (2026-11-13, the recommended option of each question).
- **Date:** 2026-11-13
- **Branch:** build-call-new

## Plan

One new class, `Build`, that turns the argument's entries into statement text; the two
primitives ask it for the text where they read `content` today, so the place, indentation,
gate, stubs and imports stay theirs. Derivation: check the sketch's unverified plan first (a
`var` draft parsed with bindings gives the type); goldens for each written form and each
refusal; `Build.statements` (entries, a draft with assignments to learn which names are in
scope, a draft with `var` to read types, the checks, the final text); `Primitives.built`
between the argument and `Build`; `Arg.BUILD` and the two definitions; the result names what
was inferred.

## Notes

- `plan:` the `var` plan checked with a scratch parse against the jar (outside the worktree):
  a local and an enhanced `for` resolve as javac does; `var x = new ArrayList<>()` is
  `ArrayList<Object>`; a call with the wrong arity gives the `var` the type `Object`, not
  recovered, while the call's own type binding is null and its method binding is the one
  candidate. So a failed call is read from the initializer, and the fallback (the expression's
  type) is what the check uses; `var` gives the declared type.
- `plan:` scope by a first draft with `into = call;`: the left side's binding says whether the
  name is a variable there (a field, an inherited one, a local), which a walk of the AST would
  have had to re-derive. Two parses when something is declared, one otherwise.
- `read:` `Primitives`, `Arg`, `Args`, `Tools` (the three definitions, `call`, the aliases),
  `GoldenTest`, `Edits.gated`, `Imports.fix` (its first 50 lines), `Extract.typeName`,
  `Surround`, all through `inspect` (file outline, then ranges or `names`).
- `bulk:` `Build.java` in one `create_file` (529 lines, type-checked on creation).
- Two batches wired it in (`add_declaration`, `replace_statements`, `replace_text`), one more
  added the tests, one the docs; each checked at the end, none refused.
- `hand:` the golden cases (resource files), written with the shell as the brief allows.
- First golden run: 11 of 14 passed; the three failures were the cases' own (a line number, the
  wording of two imports in a result, the stub's expected file).
- `refusal:` none from the tools.

## Measured

Not measured: the session-log hook is not loaded in this session.

## Assessment

Through the tools: every change to `src/main/java` and `src/test/java` went through
`create_file`, `add_declaration`, `replace_statements` and `replace_text`, read through
`inspect`. One large write, the new class. The plan held; the scope probe was added to it
before coding. What would have helped: the generators themselves, since `Build.java` is
statements typed whole.

## Follow-ups

- A checked exception the enclosing method does not declare, and an `into` of another type,
  are still the gate's compile refusals; the sketch words them with the fix.
- The loop, the guard, `replace_expression` and the skeleton refusal are the next increments.
