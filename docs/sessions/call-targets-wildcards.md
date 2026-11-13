# Calls keep their targets; change_type with wildcards

- **Item:** roadmap #146 (order 139): increment 3 of the generics sketch, the shared check and
  slice 3's wildcards. The owner decided each open question by the sketch's recommendation
  (2026-11-13); question 5a applies here: the model writes the wildcard in `type`.
- **Date:** 2026-11-13
- **Branch:** call-targets-wildcards

## Plan

A new class beside `NameCheck` that reads what each call runs before a change and requires the
same after it. `change_type` (a field in `ChangeType.run`, a method in `Signatures.changeType`)
takes the targets before its write and requires them after. The gate's fix line gains a sentence
for a wildcard. Goldens first: two applied wildcards on a parameter, one on a field, a refusal by
the gate (the body writes), a refusal by the check (an overload); two probes. First read how
`Rename.checkMeanings` behaves in a deferred batch, which the sketch left unverified.

## Notes

- read: `Rename.checkMeanings` runs after `Gate.applyOrRefuse` whether or not the gate is
  deferred: deferred, the gate only writes and journals; the check then parses the written files,
  skips a name with no binding, and a `Refusal` aborts the call (`Tools.abort`, `Journal.abort`),
  which puts back what the call wrote. `CallTargets` does the same; a golden
  (`batch_change_type_wildcard_refused_overload`) pins it inside a batch with check `"end"`.
- plan: the check is its own class, not a rule of `Behavior.check`. `Behavior` judges a `Move`
  (code a tool moves) before the write; this check has no moved code and needs the parse after
  the write. Putting it in `Gate` for every tool would cost two parses a call and cannot match
  calls across an edit that adds or removes one.
- plan: calls are matched by their order in the file and declarations by their number among the
  file's methods, since both offsets and binding keys change with the edit. A declaration outside
  the parsed files is known by its key, which the change does not alter.
- hand: the goldens and probes (resource files), and the docs.
- refusal: the first batch sent steps without `"args"`; the refusal said `replace_text needs
  file`, which was enough to see it.
- bulk: one `create_file` for `CallTargets` (179 lines); one batch of 8 steps for the wiring.

## Measured

Not measured: the session-log hook is not loaded in this container.

## Assessment

Through the tools: `inspect` for every Java read, `create_file` and one batch for every Java
write. The plan held. The check ran on every existing `change_type` golden and probe without a
new refusal. Not measured: the cost of the two extra parses on a large project.

## Follow-ups

- `GateCostTest` on a large project for `change_type` with the check.
- The other signature tools (`add_parameter`, `remove_parameter`, `move_parameter`) do not call
  the check; an added parameter can change an overload's choice too.
