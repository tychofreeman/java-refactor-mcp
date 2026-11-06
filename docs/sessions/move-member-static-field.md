# move_member of a static field

- **Item:** roadmap #109, part 2 (the user, 2026-11-05: a constant cannot be moved today).
- **Date:** 2026-11-06
- **Branch:** move-member-static-field

## Plan

`move_member` with `name` a static field (`Shop.MAX`) moves its declaration to the end of `to`
(this file or another, as for methods), widened from private (to public across packages); every
use becomes `Config.MAX`: `Shop.MAX`, a bare `MAX` in `Shop`, and a static import's name (the
import is rewritten, its file's bare uses kept). Its initializer must run alone (constants, and
new objects of them: `ExtractStruct.initializedAlone`), since class initialization order would
otherwise change what it computes; and it must be one field per declaration. Refused: an instance
field (naming `extract_struct` and `pull_up`), an initializer that reads anything else.

Derivation: goldens first; `MoveMember.field`, reached from `run` where it refused a field; the
description.

## Notes

- read: `MemberMove.removal`, `ExtractStruct.initializedAlone` (widened to package access).
- One batch for the code; the golden then failed with a syntax error: the declaration's own name
  was compared by node identity with a name from another parse (`n == frag.getName()`), never
  true, so `Config.` was inserted into the range being removed. Fixed by `isDeclaration()`. The
  session notes' trap about two parses, again, for nodes rather than bindings.
- Debugged with `bin/jrefactor --rebuild -C <scratch copy>`, which needed a temporary commit (the
  rebuild reads the working tree, but the flag came after the jar check); folded into this one.
- The #106 shift took three `old`s that Spotless's re-indent had put off by two spaces.

## Measured

Through jrefactor: 2 edit batches and the docs; no refusals; by hand the goldens (resources), a
scratch copy for the CLI, this record.

## Assessment

Through the tools. #109 is done.

## Follow-ups

- #121: one `move` over `move_type`, `move_member` and `move_file`.
