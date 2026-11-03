# Stubs travel in the call's result

- **Item:** roadmap #30, "Stubs travel in the call's result object, not
  `Stubs.said()` and a `ThreadLocal`" (decided 2026-11-03, `docs/design.md`
  Decisions). From the 2026-11-03 code review; the user asked for it.
- **Date:** 2026-11-03
- **Branch:** stubs-in-result

## Plan

The change, at the highest level: a tool call's result is an object carrying
its text and the stubs it declared, and each layer returns that object rather
than the stubs going into a shared collector that `Tools.call` drains.

Derivation, in order:

1. Golden cases already pin the stub text in `result.txt` (`stubs_*`,
   `batch_signature_end`). They stay as the check that nothing changes for the
   model (measure, before any code).
2. Add a unit test that calls a stub-declaring path and reads the stubs from
   its returned value, not from `Stubs` (red: the value does not exist yet).
3. Make the stub-declaring function return its declared stubs instead of
   recording them (`Stubs.declare` returns the list); the gate passes them up
   (`Gate.apply` returns them with the change). Green.
4. Each handler returns the result object (text plus stubs), so `Tools.call`
   reads the stubs from it and `Stubs.said()`, `DECLARED` and the ThreadLocal
   go. Refusal paths return nothing and so carry no stubs (a refused call
   changes nothing, and its stub declarations are undone with it).
5. Refactor while green: remove the now-unused side channel.

Behavior change: none for the model; the result text is the same.

## Notes

- `read:` `Stubs.declare`, `Stubs.said`, `Gate`, the `Batch` end check, and the
  call sites of `Tools.call`, `Stubs.declare` and `Gate.applyOrRefuse`
  (question: where do stubs get recorded, and where are they read?). Answer: the
  `ThreadLocal` `DECLARED` is written in `Stubs.Round.write` and drained by
  `Stubs.said()` in `Tools.call`.
- `read:` the handler sites in `Edits`, `Declarations`, `Signatures`, `Extract`,
  `Rename` and `Batch`, to find every return of a change's text (question: which
  return a stub-declaring gate's result must reach).
- `plan:` the compiler listed the sites that needed the outcome type, so the
  remaining changes followed the errors rather than a reading of every file.
  The plan's derivation held: `Stubs.declare` and `Gate.apply` return a check,
  and each handler returns `Tools.Outcome`. Refusal paths return nothing.
- `plan:` the batch's end check keeps its own `Stubs.Check`, and each step's text
  is rendered with its stubs as before (`Tools.Outcome.render`), so a batch with
  check `"each"` reads the same as before. The plan did not say this; it was
  needed to keep the output unchanged.
- `hand:` every Java change was made by hand, through Python scripts that
  replaced exact strings and failed when a string did not occur once. Tools that
  would have done each piece: `replace_text` (the handler returns, the gate
  calls) and `replace_declaration` (the `Declared` and `Check` records). No
  tool was used because the server on this branch was the build from `main`,
  not this branch; the edits change the server itself. The composition that
  would have applied these edits through the tools is a sequence of
  `replace_text` calls per site, about 20 sites, which was judged more work than
  the scripts.
- `bulk:` four Python scripts wrote 30 to 80 lines each (the record, `Stubs`,
  `Gate`, `Tools`, `Edits`, `Declarations`, `Signatures`, `Extract`, `Rename`,
  `Batch`, `Server`). All of it is changed existing code, not new behavior: the
  same logic with a different return type. A `replace_text` per site would have
  made it; none was used.
- `refusal:` none (no java-refactor-mcp call was made). The compiler's errors
  after each signature change named the next site.

## Measured

Not measured (the session log did not record this session). Test results:
`./mvnw verify` passes, 123 tests (one new: `ToolsCallTest`), spotless clean.
The golden `stubs_*` results are unchanged, which is the check that the model
sees the same text.

## Assessment

- **Through the tools or around them:** around them. Every Java change in this
  item was made by scripts that replaced text, not by the tools. The server
  runs the build of `main`, so the tools could not change their own return
  types, but a `replace_text` per site was possible and was not used.
- **Large writes:** four scripts, each 30 to 80 lines of changed existing code.
  This is the case the record exists for: the work was the same logic with a
  new return type, which an operation per site would have done, one edit at a
  time, at the cost of about 20 calls.
- **Did the plan hold?** Yes for the design (`Check`, `Declared`, `Outcome`).
  The plan missed the batch's end check and the rendering at each step.
- **Reading:** the call sites and the `Stubs` side channel. The reads were
  needed to find the sites, and the compiler then found the rest.
- **One change that would have helped:** an operation that changes a method's
  return type and rewrites its `return` statements, with its callers. Without
  it, every return site is one `replace_text`. Recorded as a follow-up on the
  roadmap.

## Follow-ups

- The review (2026-11-03, the full read by a fresh agent) raised items that
  should become roadmap entries, not this PR: `create_file` skips import fixing
  (a new file's unimported `List` becomes a declared `List.java`); overloads
  with generic parameters are split on commas; `Stubs.uses` forces import picks
  into every file; a failed Maven classpath is silent to the model. Recorded
  separately as roadmap items.
- An operation that changes a return type and its returns (see Assessment).
