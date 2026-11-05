# NameCheck: declarations in anonymous classes after an earlier edit

- **Item:** a false refusal met during roadmap #98: folding `Signatures.end`
  into its callers was refused at `ExtractStruct`, where the argument `n` is the
  parameter of a `visit(SimpleName n)` in an anonymous class.
- **Date:** 2026-11-05
- **Branch:** namecheck-positions

## Decisions (this session)

- **Cause:** JDT's keys for what an anonymous or local class declares hold the
  class's source position. An edit earlier in the same file (here, the other
  calls shortened, and the deleted method) shifts it, so the key after the edit
  differs though the name means the same declaration.
- **Fix:** `meanings` also records where each name's declaration starts, when
  it is in the fragment's file; `require` accepts a name whose declaration,
  outside the written text, maps back through the edits to such a position.
  A capture still differs: the closer declaration stands elsewhere than the one
  the name meant. Two files with a declaration of the same name at the same
  offset could hide a capture; accepted as unlikely.

## Plan

1. Red: golden `inline_function_in_anonymous_after_shift` (a call before the
   anonymous class, and one inside it).
2. Green: `at`, `before` (`add_declaration`), the two uses (`replace_text`).

## Notes

- `refusal:` the first `replace_text` declared `at` as an instance stub (the
  call is in an anonymous class of a static method); resent as one batch with
  the declaration first and check `end` (friction log).
- `hand:` none.

## Measured

All Java through jrefactor; one refusal (above), answered by reordering.

## Assessment

- **Through the tools or around them:** through.

## Follow-ups

- The stub's static-ness through anonymous classes (friction log).
