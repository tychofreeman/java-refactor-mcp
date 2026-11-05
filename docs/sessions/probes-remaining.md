# Behavior probes for the remaining tools

- **Item:** the follow-up of `docs/sessions/probes-more.md`: probes for `rename`,
  `move_parameter`, `encapsulate_field`, `function_to_method`, `extract_struct`, and
  the other signature tools, `change_type` and `convert_to_record`. The user's request
  of 2026-11-05.
- **Date:** 2026-11-05
- **Branch:** probes-remaining

## Results

16 probes. Two kinds of behavior change that compiled:

- **`rename` into an overload (fixed here).** Renaming `print(String)` to `show`
  beside `show(Object)` made the other call `show("x")` resolve to the renamed
  method: `os` became `ss`. `rename` refused only a name with the same parameter
  types; now also one the type or a supertype has for another method of as many
  parameters (`rename_into_overload`, refused).
- **`change_type` of a primitive (not fixed; a choice for the user).** `int` to
  `long` lost an overflow (`-2147483648` became `2147483648`), `int` to `double` made
  `n / 2` real division (`3` became `3.5`), `char` to `int` printed `65` for `A`.
  These three probes are not cases yet: they would fail until the rule is chosen.

Behaved, as cases: `rename` of a local to a name the method reads (refused);
`encapsulate_field` of `++`, `+=` and an assignment used as a value (refused,
conservatively); `convert_to_record` without `value_semantics` (refused);
`function_to_method` with effectful receiver and argument (applied, same output, the
null receiver reported); `extract_struct` with effectful arguments (applied, same
order); `move_parameter` with effects (refused) and with locals (applied);
`remove_parameter` of an effectful argument (refused); `add_parameter` of a name the
body reads as a field (refused).

A gap, logged: `rename` refuses a member of a second top-level class of a file ("not
in this project"); the two probes that meant to test a hidden field and a method
renamed into an override test that refusal.

## Notes

- My scratch harness put the probe under `src/main/java` with no `pom.xml`, where
  `rename` found no source root; moved to `ProbeTest`'s layout (`p/` at the root).
  Three probes had wrong lines or a missing argument, fixed.
- The read guard refused a `grep` of `Tools.java`; the description was edited with
  `replace_text` from text read earlier.
- `hand:` none.

## Measured

All Java through jrefactor (`replace_declaration`, `replace_text`); 13 cases by one
`bin/jrefactor @file` batch. `ProbeTest`: 52 cases.

## Follow-ups

- The `change_type` rule (the user's choice), then its three probes as cases.
- `rename` across nested classes: an inner class's own method of the new name would
  capture an unqualified call of the renamed outer method; not probed yet.
