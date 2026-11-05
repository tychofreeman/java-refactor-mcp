# extract_method returns several results in a record (roadmap #84, fourth part)

- **Item:** roadmap #84, widening existing tools: `extract_method` with
  several results. It refused them, by the user's choice of 2026-11-03. Asked
  again (2026-11-04), the user chose a record over keeping the refusal or an
  out-parameter holder.
- **Date:** 2026-11-04
- **Branch:** extract-method-record

## Plan

- The method returns `new BothResult(x, y)`, a `private record BothResult(int
  x, int y) {}` (the method's name, capitalized, with `Result`) declared after
  the method; the call becomes `BothResult bothResult = both(a);` and a line per
  result: `int x = bothResult.x();` for one declared in the lines, `x =
  bothResult.x();` for one declared before them.
- Refused: a record name the type already has as a member type; a holder name
  the member around the lines already uses.
- A return together with a result stays refused.

1. Red: the golden `extract_method_refused_two_results` became
   `extract_method_two_results` (`move_file` through `bin/jrefactor`, and its
   `refused.txt` deleted the same way), with the record in `out/`.
2. Green: `recordName`, `refuseRecordClash` (`add_declaration`), a `record`
   parameter on `callOf` (`add_parameter`), and `replace_text` in `plan`,
   `callOf`, `apply` and `methodText`.

## Notes

- The golden passed on the first run after the change.
- `move_file` was run from the command line: this session's MCP server
  predates it.

## Measured

From `bin/session-report` (before the docs edits): all Java through jrefactor
(one `add_parameter`, `add_declaration`s and `replace_text`s in one batch),
the golden's move and delete by `move_file`.

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes.

## Follow-ups

- A golden for a result declared before the lines (`x = bothResult.x();`).
- A method inside an anonymous class gets the record too; a record in an
  anonymous class is not allowed before Java 16, and is not tested.
