# Overload names with generic parameters

- **Item:** roadmap #36 (order 29), "Overload parameter lists split at commas
  inside generics". From the full code review of 2026-11-03, read, not run;
  this item reproduces it first.
- **Date:** 2026-11-03
- **Branch:** overload-generic-params

## Plan

The change, at the highest level: an overload named with a generic parameter
type is found the way the other overloads are, because its parameter list is
split at the commas that separate parameters and not at the ones inside `<>`.

Derivation, in order:

1. Red: golden `replace_declaration_generic_overload`, a class with
   `b(Map<String, Integer>)` and `b(int)`, named `A.b(Map<String, Integer>)`.
   Expect the refusal to show the bug.
2. Green: one helper, `Declarations.topLevelCommas`, used where `find` splits
   the name. The rest of `find` is unchanged.
3. Refactor while green: none planned; `Signatures` reaches the same `find`.

Behavior change, model-facing: a name with a generic overload now finds it,
and no refusal lists a name the model cannot type back.

## Notes

- `hand:` `Declarations.java` changed with the Edit tool (the new helper, one
  call-site line). The tool that would have done it is `replace_text` on this
  file (or `replace_declaration` for `find`); not used. Written by hand
  because the item's plan was made in a session where the edit tools were
  not yet the path for the repo's own source.
- `refusal:` the red golden was refused as the plan expected ("has no member b
  with parameters (Map<String, Integer>)"). That is the reproduction.
- `hand:` the first green compile failed on `List.of()` in a ternary; fixed
  with `List.<String>of()`. Not a tool issue.
- `hand:` the golden's expected label was my guess (`Map<String, Integer>`);
  the tool's label is `Map<String,Integer>`, as its member list shows. Kept
  the tool's label.

## Measured

Not measured (the session log is not recording this session).

## Assessment

Through the tools: the reproduction was run through the golden harness. The
change itself was an Edit-tool edit of `Declarations.java`, not a tool call,
which is the gap the friction-log entry names. No body was rewritten; one
helper and one call-site line.

Large writes: none (the helper is 17 lines of new behavior).

Reading: `Declarations.find`, `types`, and the existing overload golden,
to see the label and the call shape.

One change that would have helped: the repo's own Java going through
`replace_text` or `replace_declaration`, which the tools already support. A
guidance change, not a tool change: say in the instructions that edits to
this repo's source go through the tools too, once the server runs.

## Follow-ups

- #37 (`Stubs.uses` import picks) is next, in its own PR.
- The friction-log entry names the repo-source edit gap.
