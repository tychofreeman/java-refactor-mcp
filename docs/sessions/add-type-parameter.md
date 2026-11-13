# add_parameter with type_parameter: a class, no members

- **Item:** roadmap #156 (order 149): increment 4 of `docs/sessions/generics-sketch.md`. The
  owner decided the open questions by the sketch's recommendations (2026-11-13): 1a (on
  `add_parameter`), 3a (`Box<T>` in instance code, `Box<String>` in static code), 4a (diamonds
  where a declared type fixes the target).
- **Date:** 2026-11-13
- **Branch:** add-type-parameter

## Plan

`Tools.ADD_PARAMETER` gains `type_parameter` and routes to a new class before `ByLine.named`.
The class: parse the files that hold the name (`Holding`), find the uses by binding, write each
by the sketch's table, fix imports per file, one gate; then one parse for the diamonds, the raw
uses and `CallTargets`. Goldens for each row of the table and each up-front refusal; a unit
test for a use in a test root, since a golden has one root; one probe.

## Notes

- hand: goldens, the probe and docs. The goldens' `out` were written by running the tool on
  `in` and reading the diff, not typed first; each diff was read against the table.
- refusal: the gate refused `var v = new Box("v")` as "'var' cannot be used with type
  arguments": JDT gives `var` a `SimpleType` that binds to the variable's type. `named` skips it.
- refusal: `CallTargets` refused `new Box("x") { ... }`: an anonymous class's constructor has a
  key that holds its supertype, which the change alters. Anonymous constructors are not compared.
- refusal: a batch step `replace_text` that left `CallTargets.require` half written was refused
  ("the result does not parse") though the batch's check was at the end: each step must parse.
  Sent as `replace_declaration` of the whole method.
- read: `javac` failed the build on an unchecked `new ArrayList<>(target.typeParameters())` that
  the gate had passed: the gate counts errors, `-Werror` counts lint warnings. In the friction log.
- plan: the probe first printed an object's identity hash, which differs per run; it now prints
  only what the program decides.
- bulk: `create_file` for `TypeParameters` (about 600 lines) and for the unit test.

## Measured

Not measured: the session-log hook is not loaded in this container.

## Assessment

Through the tools for every Java read and write. The plan held; the two refusals above were
real cases the sketch's table did not name (`var`, an anonymous class).

## Follow-ups

- `members` (roadmap #157), a method's type parameter (#158), records and interfaces.
- A use inside the class names a member type with its outer type (`Box<Box.Entry>` in `Box`),
  where the simple name would do.
