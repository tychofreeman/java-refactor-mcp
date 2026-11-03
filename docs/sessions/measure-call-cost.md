# Measuring what an edit costs on Commons Math

- **Item:** roadmap #44 (order 51), "Performance (not measured)", from the code
  review of 2026-11-03. The user asked for the measurement, 2026-11-03.
- **Date:** 2026-11-03
- **Branch:** measure-call-cost

## Plan

Measure the whole path of a call, not only the gate, on Commons Math 3.6.1
(990 files, 224k lines, the sources jar from Maven Central). Count the whole-
project parses per call. Change nothing in the server.

Derivation, in order:

1. Count the parses. `TypeCheck.parses(project)` counts the gate's full
   parses; `GateCostTest.timesACall` now prints the count it made.
2. Time the gate alone (`timesGate`), then four calls through `Tools.call`
   with their undo: a rename, a one-line `replace_text`, a `create_file`, and
   an `add_parameter`.
3. Explain any call that costs more than the others by reading its code.

## Notes

- `read:` `Signatures.Family.of`, to see why `add_parameter` is slower. It
  parses every file (`Jdt.parseAll`) before the gate does.
- `read:` `Imports.fix`, to see whether its parses are whole-project. They are
  one file each, plus the file's parse with imports added.
- The count does not include the `Family.of` parse: it is a `Jdt.parseAll`,
  not a `TypeCheck` count. So the count says 1 for `add_parameter` where the
  project was parsed twice; the timing shows it.

## Measured

Commons Math 3.6.1 sources, one run each, this container (not a benchmark: a
single cold JVM per run, so the times include JIT warm-up):

| Measurement | Time | Project parses (gate) |
|---|---|---|
| `TypeCheck.errors`, cold | 9.6 s | — |
| `TypeCheck.errors`, cached | 45 ms | — |
| One edit through `Gate.apply` | 3.7 s | 1 |
| `rename` checkFinite (2 files) | 3.5 s | 1 |
| `replace_text` (1 line) | 3.2 s | 1 |
| `create_file` (7 lines) | 3.3 s | 1 |
| `add_parameter` (1 declaration, 1 call) | 7.0 s | 1 (plus 1 in `Family.of`) |

The earlier figures in the roadmap (2.1 s per edit) were measured in another
session on another machine; they are not comparable with these.

## Assessment

What the measurement says about #44's claims:

- "Each gated edit type-checks the whole project twice": not for these calls.
  Each makes one full parse for the gate; the before-count is the cache left by
  the call before it. The claim holds only for a call that comes with the
  gate's own cache missing (a first call after a change outside the server).
- "Signature changes parse again per call": yes. `add_parameter` costs about
  two full parses (7.0 s against 3.5 s), because `Signatures.Family.of` parses
  all files before the gate. That is the one cost here that a change would
  remove. Reusing the gate's parse for `Family.of` would bring it near 3.5 s.
- A parse of the whole project is about 3.5 s here, and it is nearly all of
  each call's time. Every other piece (`Imports.fix`, stubs, undo) is under
  0.1 s in these calls.

Through the tools: the measurement was a test run and reading code; no edit
to the server.

## Follow-ups

- #44 stays open for the decision: reuse the gate's parse in
  `Signatures.Family.of` (saves about 3.5 s per signature change on this
  project), or leave it until a user needs faster signature changes.
- A run that measures several times (warm JVM) would give a fairer figure.
