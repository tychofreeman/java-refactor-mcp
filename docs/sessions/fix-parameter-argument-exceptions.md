# remove_parameter and move_parameter: arguments that may throw

- **Item:** issue #475 (code review of 2026-11-13), roadmap #217
- **Date:** 2026-11-13
- **Branch:** fix-parameter-argument-exceptions

## Plan

Replace `Signatures.pure` with two questions of `Effects.of`: does the argument act (refuse, as
before) and may it throw (report through `Exceptional.change`, refused under
`preserve_exceptions`, as the recorded policy says). For a move, report when the moved argument
and one it passes could reorder a throw with a throw or an effect. Rename `ChangeSignature.pure`
to `quietAndSafe`, as the issue asks, so the two no longer share a name.

## Notes

- read: `inspect` with `names` of `Effects.changes`, `mayThrow`, `fixed`, `later`, `Effect`,
  `Exceptional.change`, `ChangeSignature.pure`; a range of the `Effects` visitor.
- plan: three probes first; two failed as the issue said (the dropped division, the swapped
  throws). The third (`"" + new Main()`) was already refused, as `new` acts; rewritten with a local
  (`"" + m`), it failed too.
- refusal: a `batch` deleting `pure` before changing its callers (safe delete names the uses);
  reordered, then `pure` and `allPure` call each other, so neither can be deleted first. Retried
  the step as one `replace_text` removing both (`retry` with `remove`).
- refusal: `rename` with `name=ChangeSignature.pure` and no `line` ("rename needs line"); sent the
  declaration's line.
- Effects does not see a string's `+` calling `toString`; kept the rule in `Signatures.acts` rather
  than adding a `CALL` to `Effects`, which every tool reads.
- The #464 row collided with `invert_if`'s 211 on `main` (both merged within minutes);
  renumbered 216 here.

## Measured

Not measured: the session-log hook is not loaded here. One batch (6 steps, after two refusals)
and one rename; no Java by hand.

## Assessment

Through the tools. Two refusals cost a resend and a retry; both said what to do.

## Follow-ups

- Safe delete of two methods that only call each other: one `replace_declaration` step per method
  is refused either way; the batch could judge the pair together.
- Whether `Effects` should count a string's `+` of an object as a call, for every tool.
