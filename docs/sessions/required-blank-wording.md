# A required string sent empty or blank reads as one left out

- **Item:** issue #622, the owner's follow-up of 2026-11-14 (left open by #620)
- **Date:** 2026-11-14
- **Branch:** required-blank-wording

## Plan

`Args` keeps the tool's `Arg` list, so `required` can refuse in `Arg.missing`'s words. Tests
first: a walk over every tool sending each required string empty and blank, expecting
`Arg.missing`'s sentence; `ArgsTest` for `required` after a route. Then the field, the
constructor `Tools.call` uses, the copies (`renamed`, `without`, `with`, `routed`) carrying it.

## Notes

- read: `Args` whole (`sed`, before the guard's rule came to mind; it is 200 lines), `Arg.missing`,
  `RequiredArgsTest` through `inspect`.
- plan: the walk showed that wording in `required` alone is not enough: most tools read `file` or
  `name` some other way first, so a blank one was refused in 26 tools' own words. `Args.check`
  now refuses a blank required string too, but `content` and `new` (a default, recorded in
  `docs/design.md`).
- refusal: `replace_text` of `return new Args(tool, out);` occurs three times; `retry` with
  `all_occurrences`.
- hand: none in Java. The golden is resources.

## Measured

Not measured: the session-log hook is not loaded here. Three `bin/jrefactor` calls for Java (a
test batch that declared the `routed(String, List<Arg>)` stub, the `Args` batch and its retry, the
`Tools` batch), none by hand.

## Assessment

Through the tools. The plan changed once the walk ran: a central check, not the wording alone.

## Follow-ups

- `Introduce` builds `new Args("add_parameter", ...)` without the tool's arguments; its refusals
  fall back to "add_parameter needs x. Pass x.".
