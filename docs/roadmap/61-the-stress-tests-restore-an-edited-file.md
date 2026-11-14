---
order: 54
item: 61
status: Done
---

## Item

The stress tests restore an edited file after a killed run: write the original to a side file first, and restore it at the next start

## Status

Done, local session

## Evidence

Follow-up of `docs/sessions/stress-narrow-check.md`: a stopped run left a Commons Math file changed. `NarrowCheckStressTest`, `RefactoringStressTest`; `StressSnapshot`, record in `docs/sessions/stress-restore.md`
