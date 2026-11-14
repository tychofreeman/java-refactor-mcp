# Loop tools: receivers by binding (issue #489)

- **Item:** issue #489 from the 2026-11-13 code review (roadmap row 225, order 217)
- **Date:** 2026-11-13
- **Branch:** fix-loop-receiver-binding

## Plan

One helper, `LoopToStream.sameVariable`: two expressions are the same variable when each is a name
or `this.f` and their variable bindings' keys match, else when their text does. Use it in the three
text comparisons of the loop tools (`ConvertLoop.unchanged`, `ConvertLoop.elementRead`,
`LoopToStream.checkBody`); `TypeSwitch.chain` is another session's file and stays open.

## Notes

- read: the sites the issue names, by `inspect` ranges and `pattern`.
- red: probe `convert_loop_to_index_this_field` (printed `CME` before, the pruned list after) and
  `convert_loop_to_each_shadowed` (a body local `items` hid the field: printed x, y before and a, b
  after); goldens `convert_loop_refused_changes_this_field`, `loop_to_stream_refused_changes_this_field`
  applied.
- plan: `owner` now returns the map's expression, not its text; `checkBody`'s `from` became unused
  and went by `remove_parameter`.

## Measured

Not measured: the session-log hook is not loaded here.

## Assessment

Through the tools. The plan held.

## Follow-ups

- `TypeSwitch.chain` compares the selector by text (`it.next()` passes): issue #489's third site.
