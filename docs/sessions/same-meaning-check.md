# Moved code keeps its meaning (roadmap #77)

- **Item:** roadmap #77, the other session's follow-up of #67: one check that
  every name in moved text resolves to the same declaration after the edit,
  for the tools that move code. Chosen after the second round of probes
  (`docs/sessions/stress-newer-tools.md`) found more name captures than the
  one-off rules cover.
- **Date:** 2026-11-04
- **Branch:** same-meaning-check

## Decisions (this session)

- **By identifier and binding key:** before, the keys each identifier has in
  the source fragments; after, a parse of the edited text, and every name in
  the written ranges must have one of its identifier's keys. Positions are not
  mapped across the edit, which keeps the check tool-independent. Skipped: names
  whose declaration is inside the written text (a moved local, a new
  parameter), and identifiers the fragments did not have (a qualifier the tool
  added).
- **Beside the one-off rules, not in place of them:** those refuse earlier, with
  a refusal that says what to change; this is the net under them.
- **Three tools first**, where the probes found captures: `inline_function`,
  `inline_variable`, `convert_lambda`.

## Plan

1. Red: goldens `inline_function_refused_captured` (a field of an anonymous
   class around the call, which `shadowEdits` does not look at) and
   `convert_lambda_to_anonymous_refused_constant` (an interface constant of
   the lambda's field's name, which `refuseSelf` does not look at).
2. Green: `NameCheck` (`create_file`), called from the three tools
   (`replace_text`).

## Notes

- The first refusal named the line in the file as it was; the check parses the
  edited text, so it names the line there (11, after the inlined method's lines
  went). The golden was set to that. An anonymous class was described as
  `Cart.`; it is now "an anonymous class".
- No existing golden changed: no false positive in the 270 cases.
- Stress run on Commons Lang (seed 9, 180 calls): no failures and no refusal by
  the check (`convert_lambda` applied 4, `inline_variable` 4). The first run made
  only 12 calls: the tools take turns, and one with no target in Lang
  (`function_to_method`) used up the tries. A tool that finds no target in 200
  files now gives its turn to the next (`RefactoringStressTest`).

## Follow-ups

- `extract_method`, `move_member`, `pull_up`, `push_down` and `move_type` call it
  too.
