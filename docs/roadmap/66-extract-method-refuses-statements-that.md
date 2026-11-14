---
order: 59
item: 66
status: Done
---

## Item

`extract_method` refuses statements that assign a blank final field (in a constructor or initializer)

## Status

Done

## Evidence

Found by `RefactoringStressTest` on Commons Math, seed 7: `Interval`'s `upper` was refused by the gate ("may not have been initialized"). `Extraction`; not on either session's list; record in `docs/sessions/extract-blank-final.md`
