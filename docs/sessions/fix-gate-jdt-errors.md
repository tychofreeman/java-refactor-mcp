# The gate refuses when the compiler throws

- **Item:** roadmap #174 (order 167), issue #405, from the Commons Lang stress run
  (`docs/sessions/stress-new-options.md`, seed 12): a change whose new text makes ECJ throw while
  the gate type-checks it was an error out of the tool call, not a refusal.
- **Date:** 2026-11-13
- **Branch:** fix-gate-jdt-errors

## Plan

The change: an exception the compiler throws while resolving is a refusal that names it and says
what to try, every file put back. Where: the one place every resolving parse goes through, `Jdt`
(`parse` and `parseAll`), so the gate and every tool that parses get it, not only `Gate.apply`.
An exception the caller's per-unit callback throws is the caller's and passes through. The calls
already put back their files on any exception (`Journal.abort`); `Gate.apply` puts back the stubs
it wrote, and `Gate.checkedAtEnd` (a batch with check "end") did not, so it gets the same catch.

1. Red: the issue's reproduction as goldens, through `change_type` with `type_parameter`, through
   `replace_text`, and through a `batch` with check "end".
2. Green: `Jdt.compilerFailed`, the two catches in `Jdt`, the catch in `Gate.checkedAtEnd`.
3. The root cause: compile the changed code with javac and see what ECJ should have reported.

## Notes

- `read:` `Gate`, `TypeCheck.count`, `Jdt.parseAll`, `Batch.steps`, `Journal.begin`/`abort`
  through `inspect`.
- `red:` the two single-call goldens failed with the NullPointerException (GoldenTest 2 of 745).
- `bulk:` the `Jdt` change as one `batch` of two `replace_text` and an `add_declaration`, check
  "end"; the `Gate.checkedAtEnd` catch as one `replace_text`.
- `read:` the root cause: javac reports three errors for the changed code (`E` no longer a
  Throwable for `throws E`; `E` of `call` outside `Task`'s bound; no functional descriptor for
  `Use::name`); ECJ 3.47.0 throws in `ASTNode.checkInvocationArgument` on the reference whose
  descriptor it could not compute. The construct the tool wrote (`E extends Object &
  Comparable<E>`) is valid Java; the code it leaves does not compile and ECJ fails to report it.
  The refusal says so in general terms, and the fix is "another type, bound or argument".
- `hand:` the docs (roadmap row, contract paragraph, session-notes line) through a Python script,
  not `replace_text`; and the rebase conflict in the roadmap table (two new rows) with `sed`.
- `stress:` `RefactoringStressTest` on Commons Lang 9368465, seed 12, 60 calls of
  `change_type_bound` only (the issue's run was 300 calls of ten entries, 18 minutes; this one
  aims at the entry that failed, 5 minutes under the heavy lock): 14 applied, 46 refused, 3 of
  them the new ECJ refusal, 0 failed; the clone clean after.

## Measured

Not measured: the session-log hook is not loaded in this session. From the notes: Java read through
`inspect` only; four tool calls wrote the Java; no hand edit to Java.

## Assessment

Through the tools for the Java. The plan held: one mechanism in `Jdt` covers the gate and the
twenty other callers of `parseAll`. The refusal cannot name the file that tripped ECJ: a batch
parse fails as a whole, and finding the file would take a parse per file.

## Follow-ups

- A newer JDT Core may not throw here; Dependabot's bump would show it (the goldens would then
  be refused by the gate's ordinary "would add compile errors", and their `refused.txt` change).
- #411 (next item): a NullPointerException from `resolveBinding` after the parse, which this catch
  does not reach.
