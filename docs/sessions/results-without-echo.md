# Results that do not echo what the model sent

- **Item:** roadmap #24, "Results that do not echo what the model sent;
  locations, not diffs", the roadmap's Next. Evidence: gorefactor's cost
  history, where smaller results were "the largest single win"; CLAUDE.md
  "Small results".
- **Date:** 2026-11-03
- **Branch:** results-without-echo

## Plan

The change, at the highest level: an edit's result says where the change
landed (file, and the line range of the new text in the file after the
edit), not the text the model sent back or a diff of it. Any result that
repeats input the model already has is removed; the rest stays as it is.

Derivation, in order:

1. Golden cases already pin each tool's result in `result.txt`. Find which
   edit results repeat their input, from the golden outputs, before changing
   any code (one operation: measure).
2. For each edit tool that echoes, change its `result.txt` golden first and
   watch it fail (red), then make the result a location line (green).
3. Build the location line once, in a shared helper that takes the file, the
   old text and the new text and returns the changed line range, so each tool
   calls it rather than formatting its own (refactor while green).
4. The one behavior change: the edit's result text. Refusals do not change.

Not in this item: a byte or token count per result (roadmap #18 follow-up);
measuring it before and after waits for the session log's token counts.

## Notes

- `read:` the 38 golden `result.txt` files, to see what each edit returns
  (step 1 of the plan). Question: does any result repeat text the model sent?
- `read:` `Batch.java` around the step result (`said`) and `region`, and
  `Edits.parses`, to see how a batch and a refusal build their text. Question:
  is the step text or a diff of the file echoed? It is not: `region` gives line
  ranges and `said` joins the step results.
- `plan:` no edit result echoes the input, so step 2 (change a `result.txt` red,
  then green) has nothing to change. The plan's one behavior change is dropped.
  The item becomes an audit: the results already report locations, and the
  remaining echo is names (a `batch` step says "renamed total to sum"), which
  the model needs to check its batch.

## Measured

Not measured. `bin/session-report` finds no log for this branch: the session
log's hook (`.claude/settings.json`) did not record calls in this session,
probably because Claude Code started before the file existed (roadmap #18).

Sizes from the golden results (bytes, the tool's whole result text):

- Edits (`add_declaration`, `replace_declaration`, `replace_text`,
  `create_file`, imports): 31 to 99 bytes for one step.
- Signature changes (`sig_*`): 106 to 165 bytes.
- Batches: 122 to 168 bytes for two steps.
- Stubs (`stubs_*`): 151 to 241 bytes; the list of declared stubs is the part
  that grows with the number of stubs.
- Largest: `inspect_outline` (958) and `inspect_lines` (463), which are reads
  and are meant to show code.

## Assessment

- **Through the tools or around them:** through the tools. No Java was written
  by hand; the edits are golden cases and this record.
- **Large writes:** none in this item beyond the record itself.
- **Did the plan hold?** No. Step 2 and the shared helper of step 3 were
  dropped: the audit found no result that repeats the input or a diff. The
  plan said the result text was the one behavior change; there is none.
- **Reading:** golden `result.txt` files and two bodies (`Batch.region`, `said`;
  `Edits.parses`), to answer the audit's question. The bodies were read to
  confirm what the result text is built from, which the golden files already
  show; the reads cost more than the question needed.
- **One change that would have helped:** the stub list in a result grows with
  the number of stubs (241 bytes for five declared in `stubs_types_and_constructors`). Capping it, with "and N more", is a
  candidate once a real session shows it large. It is recorded as a follow-up,
  not built, because no session has shown it a cost yet.

## Follow-ups

- Cap the stub list in results ("and N more") if a real session's result is
  large (`stubs_types_and_constructors` is the largest at 241 bytes).
- Batch step names echo the rename's from and to names. Keep them: the model
  needs them to check a batch, and each is short.
- Measure token cost per result once the session log records tokens
  (`docs/sessions/session-log.md`, follow-ups).
