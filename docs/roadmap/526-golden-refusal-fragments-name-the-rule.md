---
order: 516
item: 526
status: Done
---

## Item

A golden case's `refused.txt` is long enough to tell its refusal from another

## Status

Done (2026-11-16): the 60 fragments under 15 characters (`NaN`, `hides`, `array`, `break`,
`would add` ...) are each replaced by a phrase of 25 characters or more taken from the refusal
the case gets, chosen to name the rule rather than the fixture; and `GoldenTest` fails a
fragment under 15 characters. No case's refusal changed. (Items 524 and 525 are taken by the
two test PRs opened the same day.)

## Evidence

A review of the tests (the owner, 2026-11-16): 60 of 441 refused golden cases matched fewer than
15 characters, so a refusal for another reason in the same tool would still pass; `would add`
matched any gate refusal, and four cases shared it.
