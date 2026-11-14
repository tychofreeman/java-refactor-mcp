# type_switch_to_method: an if chain's selector read once per test

- **Item:** the rest of issue #489 (code review of 2026-11-13; roadmap row 225, order 217)
- **Date:** 2026-11-14
- **Branch:** fix-type-switch-chain-selector

## Plan

Probe first: `if (it.next() instanceof Circle c) ... else if (it.next() instanceof Square q)`
becomes `it.next().show()`, one call where there were two. Then in `TypeSwitch.chain`: refuse a
chain of more than one test whose selector is not a variable, and compare each test's operand by
binding (`LoopToStream.sameVariable`) in place of its text. The binding helper moves to `Names`.

## Notes

- read: the probe printed `square` where the original printed nothing; the golden applied.
- refusal: `move` of `LoopToStream.sameVariable` to `Names` asked to move `variable` and `source`
  first; `source` is used all over `LoopToStream`, so only `variable` moved (`move`, private
  widened to package), and `sameVariable` stays.
- plan: one `batch` (`replace_text` twice, `insert_statements`) for the check and the comparison.
- hand: the probe and golden resource files.

## Measured

Not measured: the session-log hook is not loaded here. Every Java change through `bin/jrefactor`.

## Assessment

Through the tools; the plan held. A field is accepted as a selector, not only a final one: the
tests run no code between them, so a field reads the same in each (a default to reopen if a
threaded case shows otherwise).

## Follow-ups

None.
