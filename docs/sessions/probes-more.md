# Behavior probes for the other tools that move code

- **Item:** the follow-up of `docs/sessions/probe-test.md`: `ProbeTest` cases for
  `extract_method`, `move_member`, `pull_up`, `push_down`, `convert_lambda`,
  `move_statement`, and `invert_if` and `remove_else`. The user's request of
  2026-11-05.
- **Date:** 2026-11-05
- **Branch:** probes-more

## Plan

Each tool's likely ways to compile and change behavior, as programs run before and
after it, first in the scratch harness, then as `ProbeTest` cases with today's
outcomes; a bug gets its probe red first, then the fix.

## Results

17 probes; 16 behaved, one bug:

| tool | probe | result |
|---|---|---|
| extract_method | two results (a record), a local used after, a `continue` leaving the range | applied, applied, refused |
| move_member | effectful receiver and argument in order; a null receiver | applied (null receiver reported) |
| pull_up | a field the subclass hides; an override of an inherited method | refused (NameCheck), refused (#69) |
| push_down | a call on a subclass instance | applied |
| convert_lambda | `this`, a bare `toString()`, an anonymous class with a field | refused each |
| move_statement | **an array element through another name**; a field through another reference; a call and a read | **applied, output changed**: fixed; refused; refused |
| invert_if | an effect in the condition | applied |
| remove_else | a then-branch that can finish | refused |

## The fix

`move_statement` swapped `int x = b[0];` above `a[0] = 1;` with `b == a`: 1 became 0;
the reverse swap too. The gate compares writes and reads by variable, and a write
to an array element has none, so neither the read moved past it nor the write moved
past a read was refused (a field through another reference was, since the field's
binding is the same whatever the reference). Now such a write counts as changing
unknown state, as a call does (`Behavior.changesUnknown`): a read of state moved
past it, and the write moved past a read of an element or field, are refused. Every
golden and probe still passes, so nothing that was safe before is refused now.

## Notes

- `hand:` none; the 17 cases written by a script into one `bin/jrefactor @file` batch.
- My `convert_lambda` probe first gave the wrong line (7 for 6).
- A check with `rm -rf $W/$c` was refused by a safety check (a variable path); rerun
  in fresh `mktemp` directories, deleting nothing.

## Measured

All Java through jrefactor: `replace_text`, `add_declaration`; cases by `create_file`
in a batch.

## Follow-ups

- Probes for `rename` (capture), `move_parameter`, `encapsulate_field`,
  `function_to_method` and `extract_struct`.
