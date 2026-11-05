# The session report counts bin/jrefactor calls as the tools

- **Item:** the friction-log entry of 2026-11-05: `bin/session-report` counted a
  Bash command that ran `bin/jrefactor` as a write by hand, so records under-counted
  tool use (three records today). The user's choice of 2026-11-05 ("Do items 1, 2,
  and 3").
- **Date:** 2026-11-05
- **Branch:** session-report-cli

## Decisions (this session)

- A Bash command that runs `bin/jrefactor` is a call through the tools: its tool and
  steps come from the words after it (`key=value`, a JSON object, or an `@file` the
  report can still read), a refusal from its output, and it is not a write by hand.
  A run whose arguments the log cannot show (an `@$S/...` file) counts as a call with
  no known steps; `list` and `undo` as calls that change nothing.
- A line of its own, `bin/jrefactor calls (in Bash): N`, so a record shows how much
  went through the command line.

## Notes

- `SessionReportTest.aCallOfBinJrefactorCountsAsTheTools` red first; my first
  assertion expected no "by hand" line, but the report always prints one, with 0.
- On the `probe-test` branch's log the report now shows 4 such calls and 0 by hand
  (it said 1 by hand).
- `hand:` none.

## Measured

All Java through jrefactor (`add_declaration`, `replace_text`).
