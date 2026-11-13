# remove_parameter: value as an expression of the other parameters

- **Item:** roadmap #147 (order 140): increment 1 of slice 4 of `docs/sessions/generators-sketch.md`,
  the query form of `replace_parameter`, as `remove_parameter` with `value` widened from a field
  or literal (#315) to an expression without calls. The user chose the sketch's recommendations
  on 2026-11-13.
- **Date:** 2026-11-13
- **Branch:** remove-parameter-value-expression

## Plan

`Signatures.removeWithValue` keeps names and literals; anything else goes to a new
`removeWithExpression`. To make that easy first: move the parameter rules out of `Introduce`
(`byParameters`, `assigned`, `parameterIndex`, `perCall`) into one class both directions use
(`move`, four calls), and split `perCall` into the check and the substitution. Then the goldens,
then: parse the value where the body starts (a probe declaration, as `ChangeSignature` probes a
type), match each call's argument against it with the call's arguments in place of the
parameters, check the effects of the arguments around it, declare the local, remove the
parameter and the arguments through `SignatureChange`.

The plan was written after reading `Signatures.removeWithValue`, `Introduce` and `Fold.Holes`,
not before: the brief named them.

## Notes

- read: `Signatures` 106-166 and 505-867, `Introduce` whole, `Fold.Holes`, `Effects.of` and
  `fixed`, `SignatureChange` outline and `apply`, through `inspect`; `Effects` 57-255 and
  `TextEdit` with `sed`, which the read guard would have denied had it been loaded.
- plan: `Fold.Holes` is not the matcher. It discovers what a parameter stands for; here the
  call's arguments give it. It does not look through parentheses (`w * (h + 1)` against
  `width * height`), and its `String.format` rule lets a parameter stand for part of a string.
  `ParameterValue.Instance` is a smaller `ASTMatcher` of its own.
- refusal: `move` of `Introduce.byParameters`: "uses Introduce.enclosing, Introduce.parameterIndex,
  Introduce.assigned, private to Introduce ... move ... first". Followed.
- refusal: `move` of `Introduce.enclosing` (a generic method, `<T extends ASTNode> T
  enclosing(ASTNode, Class<T>)`): "would add compile errors ... The method enclosing(Expression,
  Class<MethodDeclaration>) is undefined for the type Introduce". Its calls were not rewritten.
  hand: `replace_text` dropped its `private`, and it stays in `Introduce`. Friction log.
- bulk: `add_declaration` of the value reader and the matcher to `ParameterValue` (270 lines) and
  of `removeWithExpression` and `declareFirst` to `Signatures` (230 lines): new code, typed.
- hand: two stubs `add_declaration` declared inside the record `InMethod` (for helpers the next
  step declared in the class around it) could not be deleted with `replace_declaration` while
  the call still bound to them; removed with `replace_text` in the batch that replaced
  `perCall`. Friction log.
- plan: the goldens were written before the code, but not run red first: the first run was
  after the code, and passed but for the result pins.
- plan: three choices the sketch left open, each taken the conservative way and recorded in
  `docs/design.md`: always a local; a constructor passing the parameter on is refused; an
  argument of another primitive type is refused (found while reasoning about `long` parameters
  passed `int`s; probe `remove_parameter_value_argument_type`).
- plan: the effect rule is by position: an argument with effects between the arguments the
  value reads and the removed one is refused; one after them only when the value reads a field
  that is not a constant; one before them never.

## Measured

Not measured: the session-log hook is not loaded here. From the notes: 5 `move` calls (4 applied),
3 `add_declaration`, 4 batches of `replace_text`/`replace_declaration`, 1 `create_file`; goldens,
probes and docs with file tools.

## Assessment

Through the tools for `src/main`, with one hand step the tools forced (the stubs). The move
of the rules out of `Introduce` was four tool calls and no typing. The plan held except for the
matcher. One change that would have helped: `move` rewriting the calls of a generic method
(friction log).

## Follow-ups

- #315's path (a field or literal named) replaces each read with the field and does not check
  an argument with effects after it, or a body that assigns the field before a read; the
  expression path's local and position rule would cover both.
- `extract_variable` `as=parameter` now lets a call pass `this.x` or `a[i]` twice (`quiet`),
  where `perCall` had refused every kind but a name.
- Increment 2: a call in the value.
