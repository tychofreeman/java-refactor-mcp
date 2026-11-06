# preserve_exceptions: a shorter argument description

- **Item:** the user, 2026-11-06: drop everything after the comma in `preserve_exceptions`'
  description, in every tool.
- **Date:** 2026-11-06
- **Branch:** preserve-exceptions-text

## Plan and notes

The text is defined once (`Def`'s schema adds it to every refactoring): one `replace_text` makes
it "Optional: true to refuse a change to where or whether an exception is thrown." in the 24
tools that take it. `verify` passed. All published tools: 43,200 characters to 42,400.
