# Server.answerAll: an Error of one call is an engine error

- **Item:** issue #490 (code review of 2026-11-13), roadmap #235
- **Date:** 2026-11-13
- **Branch:** fix-server-catch-error

## Plan

Widen `answerAll`'s last catch to `Exception | Error`, rethrowing a `VirtualMachineError` other
than an overflow or a full heap. A `ServerTest` first: a body that throws `StackOverflowError`
gets an engine error, and the next call is answered.

## Notes

- plan: the test was red (the error escaped `answerAll`).
- refusal: `throw e` of the union type is an unhandled `Exception`; rethrown through a pattern
  variable `fatal` instead.

## Measured

Not measured: the session-log hook is not loaded here. One `add_declaration`, two `replace_text`s
(one refused); no Java by hand.

## Assessment

Through the tools.

## Follow-ups

None.
