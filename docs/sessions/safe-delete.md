# Safe delete (roadmap #83)

- **Item:** roadmap #83: delete a member or type that nothing uses, refused
  with the uses when something does (gorefactor's `remove_dead_code`). The
  user's recommendation 3 of 2026-11-04.
- **Date:** 2026-11-04
- **Branch:** safe-delete

## Decisions (this session)

- **On `replace_declaration`, not a tool.** It already deletes with empty
  content, and the gate refused a deletion that broke a caller, with the
  compile errors. What it lacked: a refusal that names the uses, and the one
  deletion the gate cannot see, an override of a concrete method (its callers
  then run the inherited one, and it compiles). `SafeDelete.check` runs before
  either delete path.
- **The uses are found by binding** in the files that hold the simple name
  (`Holding`), constructors through their calls; a use inside the deleted
  declaration (recursion) does not count.
- **Four goldens' expected fragments changed:** `stub_after_delete_inherited`,
  `stub_after_delete_outer`, `replace_declaration_delete_file_refused` and
  `replace_declaration_refused_compile` deleted a used declaration and pinned
  the gate's "would add compile errors"; they are now refused earlier, naming
  the use. The two stub goldens no longer reach the stub code that keeps a
  removed member from being declared again; that code still guards a batch
  that deletes in one step and uses in a later one.
- **Not built:** finding dead code (gorefactor's `remove_dead_code` lists
  unused declarations). Left for evidence.

## Plan

1. Red: goldens `replace_declaration_delete_refused_used` (a gate refusal
   with the old wording) and `replace_declaration_delete_refused_override`
   (applied, wrongly).
2. Green: `create_file` `SafeDelete`, and one `replace_text` in
   `Declarations.replace` to call it.

## Notes

- `read:` `Declarations.replace`, for where a delete starts; its unit is a
  syntax-only parse, so `SafeDelete` opens the file with bindings.
- Both goldens red as expected; after the change, the four old goldens
  failed only on their fragments, which were updated (above).

## Measured

From `bin/session-report` (before the docs edits):

- tool calls: 12
- through jrefactor: 15 edit steps
- refused by jrefactor: 0
- by hand: 0 Java; the record and docs by Write and Python
- large writes: 1 (`SafeDelete`, new)

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes.
- **One change that would have helped:** none.

## Follow-ups

- A dead-code finder (unused private members, unused types).
- A field read only through reflection, or a type named only in a string,
  is not a use the check sees.
