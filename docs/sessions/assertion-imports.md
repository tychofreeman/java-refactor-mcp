# An assertion imported, not stubbed

- **Item:** roadmap #117, from the A/B `shop-sonnet-skeleton` run (2026-11-05,
  `docs/sessions/ab-shop-tasks.md`): `assertEquals` in members added to a test class made by
  `declaration` became a "not implemented" stub on the test class, fought for six calls.
- **Date:** 2026-11-05
- **Branch:** assertion-imports

## Plan

The import fix treats the test framework's assertion class as a place static methods come
from, once the file uses that framework: an unqualified call that resolves to nothing, in a
file that imports from `org.junit.jupiter` (or JUnit 4's `org.junit`), is imported statically
from `Assertions` (or `Assert`) when that class has a method the call resolves to. Then the
stubs never see it.

Derivation:

1. Golden first (red): `add_declaration` of a `@Test` calling `assertEquals` to an empty
   test class (`classpath=junit`).
2. In `Imports.fix`, after `staticImports`: the framework's class, chosen from the imports
   the file keeps and adds; the call names resolved by a parse with its on-demand static
   import; each one whose method is the framework's becomes `static X.name` (`add_declaration`
   of a helper, `replace_text` of the call site).
3. Docs: the descriptions' imports sentence, the contract, the roadmap row, the notes.

## Notes

- hand: the golden's fixtures by heredoc (under `resources/`).
- read: `Imports.staticImports`, `fix`, `known`, `uncalled`, `insertionPoint` (where the new
  candidates go, and why the import landed after `Test`).
- bulk: `assertions` and `assertionClass` (49 lines) in one `add_declaration`: new behavior.
- plan: a second change, the import order: with no imports kept, the static and the plain
  import were written in name order at one place (`import ...Test;` before `import static`);
  statics now go first with a blank line (`replace_statements` of the insert loop).
- JUnit 4 has no golden: the golden classpath (`GoldenTest.junitJars`) holds Jupiter only.

## Measured

`bin/session-report` for the branch: 25 tool calls; through jrefactor 0 refactoring and 7 edit
steps; refused 0; by hand 2 (this record, the fixtures); large writes 2 (this record, the
49-line helper); denied 1 (a grep of `Tools.java`, redone with `inspect` `pattern`).

## Assessment

- **Through the tools:** all Java through the tools; the one denial was the read guard on a
  grep, answered with `inspect`.
- **Large writes:** the helper is new behavior.
- **Did the plan hold?** Yes, plus the import-order fix the golden exposed.
- **One change that would have helped:** none needed here.

## Follow-ups

- A JUnit 4 golden needs JUnit 4 on the test classpath.
