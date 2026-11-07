# create_file's existing-file refusal names add_supertype

- **Item:** the user's request (2026-11-06), from the friction-log entry of
  `repeated-constructions.md`: a test written first declared `SmsNotifier` as a stub; `create_file`
  with `declaration ... implements Notifier` was then refused as an existing file, and the models
  found `add_supertype` or `overwrite` themselves, three to six steps later.
- **Date:** 2026-11-06
- **Branch:** declaration-names-add-supertype

## Plan

When `create_file` with `declaration` is refused because the file exists, and the declaration
gives the type supertypes the file's type of that name lacks, the refusal names `add_supertype`
with its arguments, one call per supertype, so the members already there are kept. One place:
the existing-file check in `Edits` (`create_file`), which has the skeleton's text by then; its
parse gives the supertypes. Golden first (`create_file_declaration_refused_exists_supertype`),
then `insert_statements` of the hint before the `throw`, and `replace_expression` of the message.

## Notes

- refusal: the batch of three put `add_declaration` of the helper first, and its added imports
  moved line 71, which `insert_statements` named; resent with the helper last.
- bulk: `add_declaration` of `addSupertypes`, 39 lines, new behavior.
- plan: the message changed by `replace_text` of its last line, not `replace_expression`: the
  line was the string's last term, and three context lines named it once.

## Measured

`bin/session-report`: 14 tool calls; through jrefactor 3 edit steps; refused 1 (above); by hand 1
(this record; the golden is resources); large writes: `addSupertypes`, 39 lines; reads: inspect 6.

## Assessment

- **Through the tools:** every Java change through one batch; the refusal answered by
  reordering.
- **Large writes:** the helper is new behavior.
- **Did the plan hold?** Yes, but for `replace_text` in place of `replace_expression`.
- **Reading:** `Edits` lines 28-95 and the outline of `Skeleton`, to see that the skeleton's text
  exists before the check; `add_supertype`'s arguments from `Tools`.
- **One change that would have helped:** a batch that moves line numbers past an earlier step's
  added imports, as it does for the step's own lines; logged as a refusal here only, since the
  refusal named the fix.

## Follow-ups

- The other refusal in the same runs, `add_declaration` of `@Override` methods into a stub with no
  supertype ("must override or implement a supertype method"), could name `add_supertype` too,
  through `Gate.refusal` as the blank-final hint does.
