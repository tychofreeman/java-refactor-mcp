# Stub for a call that fits no overload

- **Item:** the friction-log entry of 2026-11-03 (`docs/sessions/cli-cache.md`):
  `add_declaration` of a test calling `SourceProject.load(dir, cache)` was
  refused "not applicable for the arguments (Path, Path)", where a new name would
  have got a stub. Not on the roadmap until now. Chosen with "Do them all in
  order" (the first of three, the user's answer after the order-25 exclusion).
- **Date:** 2026-11-03
- **Branch:** stub-overload

## Plan

`Stubs.find` declares a stub for a call the compiler reports as a parameter
mismatch, as it does for an undefined method: the same handler, `method`,
types the stub from the call. Operations: a golden (`stubs_overload_arguments`)
that calls `cart.add("apple", quantity)` on a `Cart` with only `add(String)`
(red: the refusal from the friction log), then `Stubs.find` handles
`IProblem.ParameterMismatch` with `UndefinedMethod`.

## Notes

- `read:` `Stubs.find` and `method` (the switch on the problem ID, and how a
  stub's label and code are built), to put the new ID in the same branch.
- `read:` the problem ID: `ParameterMismatch` is in the ecj jar
  (`org.eclipse.jdt.core.compiler.IProblem`), not in the JDT Core jar, which
  has no such class. Found with `javap` on the ecj jar.
- `hand:` one golden file (`result.txt`) had a trailing newline, which the
  expected text must not have; corrected with a one-line Python rewrite. The
  line range (8-13) was my arithmetic error (the content is five lines, 8-12),
  corrected with `replace_text`.
- `plan:` none changed.

## Measured

Not measured: no session log for this branch.

## Assessment

Through the tools: the golden was made with `create_file` and edited with
`replace_text`; the code change is one `replace_text`. The one hand write is
the newline fix above, which no tool does for a golden result. The plan held.
A declared stub that is an overload changes behavior for a real mistake in
arguments too: a wrong call now gets a stub overload instead of a refusal.
That is the trade the friction entry asked for; it is worth watching.

## Follow-ups

- A refusal that names the existing overloads, when the mismatch is in a
  call that is not new code (open question for the user).
