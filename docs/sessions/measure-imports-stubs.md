# Measure import lookup and stub rounds

- **Item:** roadmap #44, the part left open: `Imports.toImport` and the stub
  rounds were not measured. Chosen from the open row after order 32.
- **Date:** 2026-11-03
- **Branch:** measure-imports-stubs

## Plan

No code changes. Time one `add_declaration` on Commons Math 3.6.1
(`MathUtils`, 990 files) through `GateCostTest` in three cases:

1. baseline: a method body with no import and no missing name;
2. import: a body using `BitSet`, so `Imports.toImport` finds `java.util.BitSet`;
3. stub: a body calling `noSuchHelper`, so a stub round declares it.

Each case is one run; the call and its undo are both timed.

## Notes

- `read:` `GateCostTest.timesACall` and `MathUtils` line 37, to choose the
  file and the call JSON. Reading the harness to know what it prints.
- Measurement was run with `./mvnw -q -Dtest=GateCostTest` and the call
  as `-Djrefactor.cost.call`, one process per case. No edit was made by
  hand; the scratch sources are a copy in the scratchpad, not this repo.

## Measured

Commons Math 3.6.1, one run per case:

| case | call | project parses | last check | undo |
|------|------|----------------|------------|------|
| baseline | 267 ms | 1 | 2 files | 2929 ms |
| import (`BitSet`) | 959 ms | 1 | 34 files | 2773 ms |
| stub (`noSuchHelper`) | 470 ms | 2 | 1 file | 2714 ms |

The session-log hook was not measured: the `bin/session-report` count is
not available for this session, so the record counts from these notes.

## Assessment

- **Through the tools:** the measurement is a tool itself (`GateCostTest`),
  no Java was written by hand, so there is nothing to log as `hand:`.
- **Import lookup:** the import case costs about 0.7 s more than the
  baseline. Its last check parsed 34 files, so the cost is probably the
  parse of those files, not the index; the run does not split the two.
- **Stub round:** about 0.2 s more, and one more project parse, which fits
  the stub being declared and checked again. It is not a cost to act on.
- **Undo:** reported as 2.7-2.9 s; that was the harness's check, not the undo (see Correction).
- **Single runs:** each figure is one run on one machine, so differences
  under about 100 ms are noise.

## Correction

The undo column above is wrong. `GateCostTest` started the undo timer before its
own `uncached` check, which parses the whole project. `Journal.undo` takes under
1 ms; see `docs/sessions/measure-undo.md`.

## Follow-ups

- Undo cost: resolved in `docs/sessions/measure-undo.md`.
- Splitting the import case into index and parse would need a counter in
  `Imports`; not added, since the total already answers the item.
