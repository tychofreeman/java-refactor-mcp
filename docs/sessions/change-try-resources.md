# change_try: add or remove a resource of a try-with-resources

- **Item:** roadmap #206 (issue #364's later step; row 199 built the rest of `change_try`). The
  user chose, from options set out to them on 2026-11-14: `remove_resource` "move it out and close
  it" (over moving it out without closing it and over refusing when the body uses it), and
  `add_resource` "text with a position" (over declaration text only and over text or a moved-in
  local).
- **Date:** 2026-11-14
- **Branch:** change-try-resources

## Plan

Two more changes of `ChangeTry`, each a text edit through `Primitives.gated` as the others are.

- `add_resource`: insert the text at its place in the list (`try {` gets `(text) `); refuse a
  position outside 1 to n+1, and a declared name the body or a later resource already uses.
- `remove_resource`: JLS 14.20.3 translates `try (R a; R r; R b) body catch finally` as a try
  around `try (a) { R r = ...; try (b) body finally-close r }`; write that as text, and drop the
  outer try when it keeps no resource, catch or finally (the user's form). `if (r != null)` unless
  the initializer is a `new`. Name check for the declaration when it lands in the enclosing block.
- Probes run every order of opening, failing and closing (open, null, body, close throwing) for the
  first and the last resource, a lone one, and a Java 9 variable; one probe expects the change the
  form cannot avoid (suppression).

Derivation: no refactoring first (the change is additive: two cases in `run`'s switch, an `Edit`
note); red goldens and probes; one batch for the code; then trim the definition under its limit.

## Notes

- read: `ChangeTry` whole (as the item's own file), `Primitives.indent`, `gated`, `line`,
  `ThrowsCallers.step`, `catchAt` (its nested try), `Indent`, `Exceptional`, `Outcome`.
- hand: 11 goldens and 5 probes by a Python script (resource files); docs by a script.
- bulk: one `batch` of 7 steps (CHANGES, Edit, the refusal, the switch, seven new methods by
  `add_declaration`, the description and arguments): passed first time, "no new compile errors".
- red: 11 goldens and 5 probes failed; after the batch, every golden passed; the first probe failed
  on "close a close b" (the removed resource's close() throwing while the other's does): the
  suppression change, which moved into the `changed` probe; the last resource keeps it (pinned).
- refusal: `replace_text` of the arguments after `spotless:apply` had rewrapped them; `batch` `retry`
  with the step's new `old` passed.
- plan: the definition was 2,017 characters, over `definitionsStaySmall`'s 1,700; the description
  was cut to the tool's whole behavior in one sentence per kind and the arguments' text shortened
  (one `replace_declaration` of `Tools.CHANGE_TRY`), not the limit raised.
- refactor: `inline` of a local (`later`) the batch had written.

## Measured

Not measured (the session-log hook is not loaded here). From the notes: through `bin/jrefactor`,
two batches (one with a retry), one `replace_declaration`, two `replace_text`s, one `inline`; about
a dozen `inspect` reads; by hand, the goldens, the probes and the docs (none of them Java).

## Assessment

- **Through the tools:** every Java change.
- **Large writes:** seven new methods (about 220 lines) in one `add_declaration`: new behavior.
- **Did the plan hold?** Yes, but for one choice the plan made the probes settle: the user's
  description put the close first in an existing finally; the translation puts it in a new inner
  try, and the probes show why (catch order, the initializer's exceptions, a throwing close()).
  Recorded as a default to reopen.
- **One change that would have helped:** none new.

## Follow-ups

- Reopen the inner-try default if the user prefers the literal form, reported.
- Suppression could be kept exactly by writing the JLS's whole translation (a `Throwable` primary,
  `addSuppressed`); not built, since the user chose a close in a finally.
