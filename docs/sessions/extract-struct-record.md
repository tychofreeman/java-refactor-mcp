# extract_struct with fields on a record

- **Item:** roadmap #118, from the A/B `shop-sonnet-skeleton` run (2026-11-05,
  `docs/sessions/ab-shop-tasks.md`): the address task's model tried `extract_struct` with
  `fields` on the `Order` record first and was refused ("not a top-level type ... that
  resolves").
- **Date:** 2026-11-05
- **Branch:** extract-struct-record

## Plan

Extract class for a record: consecutive components become one component of a new record, in a
file beside it (public when the record is). Every construction `new Order(a, s, c, t)` passes
`new Address(s, c)` in their place, every accessor call `o.street()` becomes
`o.address().street()`, and a read of the field in the record's body `street` becomes
`address.street()`.

The user's choice (2026-11-05, over doing it and reporting the change, and over generating a
`toString` that prints the old text): the record's generated `toString` changes, so the call is
refused unless the record defines `toString` or passes `value_semantics=true`, as
`convert_to_record` does. `equals` and `hashCode` keep their meaning.

Refused in this first version, saying why: components not next to each other (arguments would
run in another order), an explicit constructor (canonical, compact or other: `this(...)` and
the assignments would need rewriting), a method reference to an accessor or `Order::new`, a
record pattern of the record, and a type the new file cannot name (`unusable`).

Derivation:

1. Goldens first (red): the move across two packages (an import of the new record), and the
   four refusals.
2. `ExtractStruct.fields` branches to a new `components` for a record (`replace_text`); the
   method by `add_declaration`: find the record, check, collect the edits per file through
   `Holding`, write the new record, then each file's text with its imports fixed, through the
   gate.
3. `extract_struct`'s description and `value_semantics` argument; the contract, roadmap, notes.

## Notes

- hand: four goldens' fixtures and one probe by heredoc (under `resources/`).
- read: `ExtractStruct.run`, `fields` and `unusable` (to reuse the checks and the write
  order: the new record first, then the files that name it, so their imports resolve);
  `Opened.topLevel`, `Holding.of`, `Gate.applyOrRefuse`, `Journal`'s `begin`/`abort` (a refusal
  after the first write undoes it).
- refusal: the batch (branch, `components`, the `Def` argument) refused for a duplicate local
  (`declared`); resent with it renamed: 236 lines sent again.
- refusal: the resent batch refused for the `Def` step's `old`, two spaces too deep (the
  separator trap, roadmap #106); that step sent alone, then the other two again: the 236 lines
  a third time. Friction-log entry.
- bulk: `components` (236 lines) in one `add_declaration`: new behavior, sent three times.
- refusal: a `replace_text` of the session notes with a guessed `old`; resent from `grep`.
- The #105 import choice worked unasked: `MethodInvocation` was taken from
  `org.eclipse.jdt.core.dom` over JUnit's, "the file imports from" that package.
- plan: a behavior probe added (`extract_struct_record_order`): argument order, accessors,
  `equals` and `hashCode` the same before and after.

## Measured

`bin/session-report` for the branch: 30 tool calls (before the docs); through jrefactor 0
refactoring and 7 edit steps; refused 3, each resent through the tools; by hand 2 (this record,
the fixtures and probe); large writes 3 (this record, `components` 236 lines, the probe).

## Assessment

- **Through the tools:** all Java through the tools; no hand edits after a refusal.
- **Large writes:** `components` is new behavior, but it went out three times for two one-line
  mistakes, the batch being all or nothing. That is the session's waste, logged.
- **Did the plan hold?** Yes; the constructions are wrapped by two insertions rather than one
  replacement, so an accessor call inside an argument can be rewritten in the same pass.
- **One change that would have helped:** a batch that keeps the steps before a refused one,
  or a refusal that names the fix as a later step (roadmap #110; friction log of this date).

## Follow-ups

- An explicit constructor (compact or canonical) rewritten, not refused: the most common
  record with validation.
- Method references and record patterns rewritten, not refused.
