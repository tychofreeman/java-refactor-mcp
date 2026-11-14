# inline keeps text blocks as they are

- **Item:** issue #477 (code review, extract-inline #5, the text-block risk only):
  `InlineFunction.reindent` shifted every line that starts with the body's indentation, those
  inside a text block too, which can change the string's value.
- **Date:** 2026-11-13
- **Branch:** fix-inline-text-block

## Plan

The change: the re-indentation skips the lines inside a text block, as `Extraction.reindent` does.
Derivation: probes first (a void method and one with a value, each a text block whose content and
closing delimiter sit left of the body), then `textBlocks` (each text block's source in the body),
`add_parameter` `blocks` on `reindent` with that as the value at both calls, and its body rewritten
to skip lines that start inside one. The shared `Lines.shift` the review suggests is the Indent
issue, not this one.

## Notes

- read: `inspect` with `names` for both `reindent`s, then the lines around the two calls in
  `InlineFunction.replacementFor` (`whole` is the body there).
- plan: both probes failed on main (`"abc\n    def\n"` printed as `"abc\n      def\n"`).
- `InlineFunction`: one `batch` (`add_declaration` of `textBlocks`, `add_parameter` `blocks` with
  value `textBlocks(whole, declaringText)` at both calls, `replace_declaration` of `reindent`).
- hand: golden and probe resource files, and the docs (plain files, as the brief says).

## Measured

Not measured: the session-log hook is not loaded here. One tool call wrote all the Java.

## Assessment

Through the tools. The plan held: `add_parameter` with a value expression carried the new
argument to both calls in the step that added it.

## Follow-ups

- The seven re-indentations (the Indent issue) remain; this fix is local to `InlineFunction`.
