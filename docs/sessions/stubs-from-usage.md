# Stubs from usage

- **Item:** roadmap #21, position 5 of the eight the user asked for (2026-11-02).
- **Date:** 2026-11-02
- **Branch:** stubs-from-usage

## Plan

At the highest level: a test written first calls what does not exist yet, and
the gate refuses it. As in gorefactor, edits declare what is missing
themselves, so TDD's red step goes through the tools in one call. The user
chose automatic stubs only (2026-11-02), over automatic plus a
`create_from_usage` tool, or the tool alone: a tool waits for friction-log
evidence that inference picks the wrong place or types.

When an edit (or a batch checked at its end) would add compile errors, the gate
first declares stubs for the uses nothing declares, then checks again; it
refuses only for what is left. A stub's body is
`throw new UnsupportedOperationException("not implemented");`, which
`add_declaration` already replaces. Declared, from the use:
- methods on the project's classes: `x.m(a)` on x's type, `T.m(a)` static on T,
  `m(a)` on the enclosing type (static in a static context). Parameter types
  are the arguments' types, names the argument's name when it is a variable;
  the result type comes from where the call sits (a statement: void; a
  declaration, assignment, return, comparison or arithmetic: the other side's
  type; a condition: boolean; an argument: the parameter's type, or for
  `assert*` the other argument's), else Object;
- constructors `new T(a)` on the project's classes (and `T() {}` when T had
  only the implicit one, so other `new T()` keep working);
- classes nothing declares, in the using file's package under the first source
  root (`src/main/java`), as `public class T {}`; their members follow in the
  next round (up to five rounds).
Not declared (the error stays, the gate refuses): methods on interfaces,
library types, fields. The result says what was declared: "; declared stub
int total() in Cart (com/example/Cart.java)".

Derivation:
1. Green: `Declarations.add` builds its text inline. Extract `plan` (the new
   text and its changes) so stubs reuse it without the gate.
2. Red: golden cases (an instance method with a result from a declaration, a
   static one, one on this type, a void call, a constructor, a new class with a
   method, assertEquals's other argument, an interface method refused). Stub
   `Stubs.declare` returning nothing declared.
3. Green: `Stubs` (create_file); `Gate.apply` and `Batch`'s end check call it;
   `Tools.call` adds what was declared to the result.
4. Docs: contract, tool descriptions (create_file's "a file that uses what
   nothing declares yet is refused"), CLAUDE.md step 4, roadmap #21 Done.

## Notes

- `refusal:` a batch with `imports` at the top level; resent with it on the step.
- `refusal:` the `Stubs` body, "old is not in": spotless had reformatted the
  stub I wrote, so the copy I had was stale. Read with `inspect`, resent.
- `refusal:` `ConditionalExpression` ambiguous (dom or the internal compiler
  AST); resent with every dom name in `imports`.
- Three old golden cases that expected "would add compile errors" passed once
  stubs existed: two (`missing()`, `new Missing()`) now use a type mismatch;
  the third (deleting `b()` that `a()` calls) found a real bug, a removed
  method declared again as a stub. Fixed: a member the edit removed is not
  declared (`Round.removed`), and a batch passes the texts from before it
  (`Journal.written`).
- `hand:` none. Reads: `Gate`, `TypeCheck`, `Server` and the golden files by
  `cat` (small), Java through `inspect`.
- Probe: three Haiku runs, $0.005-0.007 each; the third, with file paths, wrote
  the test in one `add_declaration` and got `int total()`, `int count()`.

