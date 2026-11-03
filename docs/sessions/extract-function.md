# Extract method

- **Item:** roadmap #23, position 7 of the eight the user asked for (2026-11-02).
- **Date:** 2026-11-03
- **Branch:** extract-function

## Plan

At the highest level: move a run of whole statements into a new private method
after the one they are in, and call it in their place, as gorefactor's
`extract_function` does. Java calls it extract method, so the tool is
`extract_method` (as `rename_local` is Java's narrower `rename`).

Arguments: `file`, `start_line`, `end_line` (inclusive, as `inspect` numbers
them), `name`, optional `doc` (the new method's Javadoc).

Rules, from gorefactor's `docs/checks.md` where Java allows:
- The lines cover whole statements of one block (a body, a `{}` block, a
  `case`); blank lines and comments in the range are allowed. A range that cuts
  a statement is refused with the statement lines nearby.
- Parameters: the locals declared outside the statements that they use, in
  order of first use, typed from their bindings.
- Result: a local the statements assign, declared outside them, that is used
  after them (or anywhere, when they are in a loop inside its scope), or one
  they declare that code after them uses. At most one: Java returns one value.
  The user chose (2026-11-03) to refuse two or more, naming them, over
  returning a record of them.
- Static exactly when the enclosing method or initializer is static. The user
  chose this (2026-11-03) over "static whenever the statements use no instance
  member", which needs an instance-use check whose misses only the gate catches.
- `return` in the statements: in a method with a result, the new method has
  the same result type and the call is `return m(...)`; the gate refuses it if
  some path falls off the end. In a void method, only when the statements end
  the method's body (the call is then the last statement). `break`, `continue`
  and `yield` to a target outside them are refused.
- `throws`: the checked exceptions the statements throw and do not catch.
- Type parameters of the enclosing method are copied when the statements use
  them.
- The name must be a Java name that no method of the type or its supertypes
  has (an overload could change which method other calls pick).
- The statements' text moves, re-indented, comments included.
- Result: `extracted lines a-b of F into name(params) -> type, lines x-y`, with
  a note when there are more than five parameters.

Derivation:
1. Green: nothing to prepare; the tool is new code.
2. Red: register `extract_method` with a handler not written yet (its stub is
   declared by #21), and golden cases: plain, a parameter and a result, a
   declared result, return in a non-void method, a static method, a
   checked exception, and refusals (cut statement, two results, break out,
   name clash, return in a void method's middle).
3. Green: `Extract`.
4. Docs: contract, CLAUDE.md, roadmap #23 Done.

## Notes

- The user asked mid-item (2026-11-03) for pull up / push down on the
  roadmap, and noted Go and TypeScript may want it too: added as #25 in
  `docs/design.md` by hand (the Edit tool), not through `replace_text`.
- Registering the tool with `Extract.method` before `Extract` existed: the
  gate declared the class and a `String method(SourceProject, Args)` stub.
- `refusal:` `create_file` of `Extract`: a static `statements(List)` clashed
  with the record's `statements()`, and `Declarations.step` was private.
  Resent whole in a batch (check "end") with `step` made package-private: the
  1000-line file went twice.
- The ten golden cases passed at the first run of `Extract`; three more
  (loop, lambda, a return on some paths) followed, the last red until a
  `terminates` check replaced the gate's "missing return" error.
- `hand:` the roadmap row for #25 (Edit tool). Reads: gorefactor's
  `docs/checks.md` by `sed`; Java through `inspect`, except `grep` for
  line numbers.
- Probe: Haiku, "the subtotal computation in render should be its own
  method": one `extract_method` call, `double subtotal = subtotal();` (6
  turns, $0.004).
