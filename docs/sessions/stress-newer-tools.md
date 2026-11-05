# Stress and probes of the newer tools (after #80-#91)

- **Item:** the user said "continue" after the 2026-11-04 list was done; the
  rule in the session notes (a tool that moves code needs probes for evaluation
  order and name capture, not only a stress run) made this the next step.
- **Date:** 2026-11-04
- **Branch:** stress-newer-tools

## Plan

1. `RefactoringStressTest` calls the newer tools on random targets:
   `convert_lambda`, `function_to_method`, `extract_struct` (two neighbouring
   parameters), `convert_to_record` (with `value_semantics`), `extract_supertype`
   (an interface of one public method), `move_type` with `package`,
   `extract_variable` `as=constant`; `inline_function` now picks instance
   methods and bodies of several statements too.
2. Probe the meaning-changing cases the gate cannot see, by hand, with
   `bin/jrefactor` on scratch projects.

## Notes

- Stress (seed 5, 180 calls each on Commons Lang and Math): no failures. On
  Math every new tool applied at least once (`function_to_method` 10,
  `move_type` to a package 8, `convert_lambda` 7, `extract_supertype` 7,
  `extract_struct` 6, `as=constant` 5). Three `extract_struct` calls were
  gated: the record's components used a method's type variable or a private
  nested type (#95).
- Probes, each applied and type-checked while changing behavior:
  - `function_to_method` of `static int area(Shape s, int k)` with a subclass
    `Square` that has `int area(int k)`: `Square.area` now overrides it, and
    `x.area(3)` on a `Square` returns 9, not 3 (#92).
  - `inline_function` of `total()` (`return count * 2;`, `count` a field) into
    `f()`, which has a local `count = 10`: the result reads the local (#93).
  - `convert_lambda` of `() -> label()` (an outer method) to an anonymous `Act`
    whose superinterface has a default `label()`: the call now means the
    default (#94).
  - `extract_variable` `as=constant` in an enum: the constant went after the
    constants, correctly.
- `hand:` the probe projects, by `printf` in the scratchpad.

## Measured

Test changes through jrefactor (`replace_text` steps); the probes by the
command line.

## Assessment

- The stress run again saw only compile errors; the probes found three
  behavior changes, as last time. The rule in the session notes holds.

## Follow-ups

- Roadmap #92-#95.
