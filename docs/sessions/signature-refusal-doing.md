# The signature tools' refusals say what they were doing

- **Item:** roadmap item 510's follow-up (issue #643, PR #648): the gate refusals of
  `add_parameter`, `remove_parameter`, `move_parameter`, `change_signature`, `extract_type` and
  `function_to_method` read "added int retries to ... would add compile errors, so nothing was
  changed"; the owner asked for the present participle, as `change_type` has.
- **Date:** 2026-11-15
- **Branch:** signature-refusal-doing

## Plan

`SignatureChange.apply` has three forms; only the six-argument one takes `doing`. Make the change
easy first (green): inline the five-argument and then the three-argument overload, so each caller
passes `what` twice, the second as `doing`. Red: one refusal golden per tool, each a change that
adds a compile error, `refused.txt` holding the "-ing" fragment. Green: `replace_expression` of
each caller's last argument. Refactor: inline the locals the inlining left.

## Notes

- plan held: inline (5-arg) then inline (3-arg) left one `apply`; the 3-arg one was first refused
  ("uses a member of SignatureChange", `FIX`): `replace_expression` `FIX` to `SignatureChange.FIX`
  in the same batch, then the inline went.
- refusal: `replace_expression` takes `expression`, not `old`; `batch` takes no `report`. Both
  said so; one retry each.
- read: the goldens' first versions met other refusals (two methods named alike: `name` needs the
  parameter types; `extract_type`'s own "already names size"); a nested class named as the new
  record made its gate refuse.
- `inline` of a single-use local into an argument wrapped the string concatenation in
  parentheses; four `replace_expression`s removed them (friction log).
- hand: none in Java. Golden resources and docs written as plain files.

## Measured

Not measured: the session-log hook is not loaded here. From the notes: 5 tool calls that changed
Java (2 inlines, 3 batches: 8 + 4 + 4 steps, plus the retry), 0 hand edits.

## Assessment

Through the tools throughout; the plan from one read held. The inline's parentheses cost one
batch.

## Follow-ups

- `inline` of a variable into an argument position need not parenthesize a binary expression
  (friction log, 2026-11-15).
