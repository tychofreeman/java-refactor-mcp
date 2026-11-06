# A class stub from a test goes in the main root

- **Item:** roadmap #113 (friction log 2026-11-05: `create_file` of a test using an undeclared
  `Cart` put `Cart.java` in `src/test/java`, and no tool moves Java between roots).
- **Date:** 2026-11-06
- **Branch:** test-stub-main-root

## Plan

The user's choice (2026-11-06), over always the main root (a real test helper would then need
moving back, and no tool moves Java between roots) and over keeping the test root and adding a
move (test-first of a production class always one extra call): a class a test uses first goes
in the main source root, the test's package, unless its name reads as a test double or helper
(`Fake*`, `Stub*`, `Mock*`, `Dummy*`, `Spy*`, `*Test`, `*Tests`, `*Fixture`, `*Helper`, `*Fake`,
`*Stub`, `*Mock`), which stays in the test root as before.

Derivation: a `StubsTest` case first (`Cart` from `ShopTest` lands in `src/main/java`; the
existing `Helper` case pins the exception); then `Stubs.Round.type`'s root choice
(`replace_statements`) and a `TEST_DOUBLE` pattern (`add_declaration`).

## Notes

- read: `Stubs.Round.type` and `StubsTest` (which pinned the test root on purpose, so the rule
  was put to the user).
- refusal: the batch, for `Pattern`: #105's package rule chose `org.eclipse.jdt.core.dom.Pattern`;
  resent with `imports`. Friction-log entry (2026-11-06).

## Measured

Through jrefactor: one `add_declaration` (the test), one batch refused then resent, the docs;
by hand this record and one roadmap cell by script.

## Assessment

Through the tools. The refusal was the import rule's guess, caught by the type check; logged.

## Follow-ups

- The import guess (friction log 2026-11-06): try another candidate when the pick does not
  type-check.
