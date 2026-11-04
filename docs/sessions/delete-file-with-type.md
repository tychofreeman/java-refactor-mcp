# Deleting a file's only type deletes the file

- **Item:** the friction-log entry "a test helper's stub went to src/main,
  and no tool deletes a file" (2026-11-04, `docs/sessions/stress-restore.md`),
  and the hand `git rm` of golden directories in #57: `replace_declaration`
  with empty content deletes a declaration, but a file whose only type it
  was is left holding its package line and imports. Chosen by the user
  (2026-11-04).
- **Date:** 2026-11-04
- **Branch:** delete-file-with-type

## Plan

The change, at the highest level: deleting the only top-level type of a
file deletes the file, through the gate (so a type still used is refused)
and the journal (so `undo` puts it back).

1. Red: golden `replace_declaration_delete_file` (`Old`, unused, with an
   import and Javadoc: the file goes, "deleted Old and its file
   com/example/Old.java (was lines 5-8)") and
   `replace_declaration_delete_file_refused` (`User` uses `Old`: refused,
   the file kept, and no stub declares `Old` again).
2. Green: in `Declarations.replace`, a top-level type that is the file's
   only one is deleted by `Gate.applyOrRefuse` with the file's text null.

## Notes

- test: the delete case red as planned (the file stayed); the refused case
  passed before the change, as a guard.
- plan: the change made the guard fail: with the file gone, the stubs
  declared `Old` again for `User`, and the deletion applied. Fixed in
  `Stubs.type`: a file the edit deleted held the class, so it was removed,
  not missing.
- found: `GoldenTest` undoes each step and compares with `in/`, so the case
  also shows `undo` restoring the file.

## Measured

- tool calls: 20
- through jrefactor: 0 refactoring steps, 13 edit steps
- refused by jrefactor: 0
- by hand: 1
  - Bash: cd /home/cwfreeman/dev/ai-playground/java-refactor-mcp && cat > docs/sessions/delete-file-with-type....
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 0
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: python3 - <<'EOF'
- reads: Bash java 6, inspect 2

## Assessment

- **Through the tools or around them:** all through java-refactor-mcp.
- **Did the plan hold?** Yes, plus the stub fix the guard case caught.

## Follow-ups

- A directory (golden cases) still has no tool; `git rm -r` remains the way.
