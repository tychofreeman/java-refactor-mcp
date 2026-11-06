# replace_text: gorefactor's spacing rule

- **Item:** the user, 2026-11-06: for replace_text's indentation misses, adopt gorefactor's
  approach (any run of spaces may stand for any other) in place of #106's uniform shift. And,
  asked about `new` after a loose match (Go writes it as sent): re-indent it to the file, over
  writing it exactly as Go does. This reverses #106's choice (2026-11-06, the uniform shift over
  Go's rule); the options it beat are recorded there.
- **Date:** 2026-11-06
- **Branch:** replace-text-go-spacing

## Plan

`old` is found exactly first (an exact match starting inside a line's indentation still does not
count, #119); else with the spacing within lines ignored, each run of spaces and tabs standing
for any other and line breaks kept (gorefactor's `spacingIgnored`). When that match is used,
`new`'s lines are moved by the difference between the indentation of the file's first matched
line and `old`'s first line, their own relative indentation kept, and the result says the match
was loose and how `new` moved. A miss still names the nearest lines; several loose matches are
refused as several exact ones are.

Derivation: the goldens' expected results first (spacing within a line now matches; the shift
cases say the new wording; #119's refusal becomes the "occurs 2 times" one); `IndentShift.find`
replaced by a loose `locate` (`replace_declaration`), the shift kept for `new`; its unit test
follows.

## Notes

- read: `Edits.replaceText`'s search, `IndentShift`'s outline.
- refusal: the batch's fourth step (a line the third step's range already removed). Answered
  with `retry`, replacing step 4 with the contract's update; refused again for
  `IndentShiftTest`, which still called `find`; answered with `retry` adding a step after 4
  (the test class rewritten); applied. The long steps (the pattern, the search) went once.
- Four goldens' expected wording followed; `replace_text_spacing_within_line` added (spacing
  inside a line, which the uniform shift refused).

## Measured

Through jrefactor: one batch and two retries (5 steps applied), one description batch; by hand
the goldens (resources) and this record.

## Assessment

Through the tools, and the first live use of `retry` here: two refusals cost two short calls.

## Follow-ups

None.
