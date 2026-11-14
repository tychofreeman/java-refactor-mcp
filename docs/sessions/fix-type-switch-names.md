# type_switch_to_method never ran NameCheck

- **Item:** issue #468 (code review of 2026-11-13, control-flow #2), roadmap #223
- **Date:** 2026-11-13
- **Branch:** fix-type-switch-names (stacked on fix-type-switch-order, #467)

## Plan

Three small changes in `TypeSwitch`, each pinned by a probe that changes behavior today:
`NameCheck.require` over the edits, with the case bodies' meanings, before the gate (as
`EnumSwitch` does); `usesOnly`'s `own` by binding key rather than identifier; and `asReceiver`
writing `this.x` for `c.x` when the body declares an `x`, since `NameCheck` keys by identifier and
the local's key is among `radius`'s meanings already (`c.radius` and the local share the name), so
it cannot see that capture.

## Notes

- plan: the issue's example is a switch expression with a block case, which `TypeSwitch` refuses
  (blocks only in a switch statement); the probes use switch statements. All three probes printed
  another value after the rewrite: 4.0 for 6.0, 3.0 for 10.0, 5.0 for 1.0.
- One `batch` of three `replace_text` steps (check `end`) for `usesOnly` and `asReceiver`, one
  `replace_text` for the `NameCheck` call. `undo` of that call, to see the static-capture probe
  fail without it, then the same call again.
- The applied golden's `out/` was the CLI's result (`--rebuild`) on a copy of `in/`, read and
  copied.
- Docs by hand: not Java.

## Measured

Not measured: the session-log hook is not loaded in this session. Four tool calls to `src/main`
(one an undo and its redo), no hand edit of Java.

## Assessment

Through the tools. The plan held, with the one finding above: `NameCheck` alone would not have
caught the issue's own example, so `asReceiver` needed its own rule.

## Follow-ups

- A use of the pattern variable inside an anonymous class in a case's body: `c.toString()` becomes
  `toString()` and a bare `c` becomes `this`, both meaning the anonymous class there. Not fixed
  here (not in the issue); `usesOnly` refuses a written `this`, not one `asReceiver` writes.
