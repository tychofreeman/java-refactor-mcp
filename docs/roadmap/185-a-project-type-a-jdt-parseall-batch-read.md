---
order: 178
item: 185
status: Done
---

## Item

A project type a `Jdt.parseAll` batch read from the source path answered no methods to `getDeclaredMethods()` once the batch ended (JDT freed its scope and logged "Could not retrieve declared methods"), so a tool deciding on that list decided on an empty one, silently (issue #431; `Jdt.parse` of one file was not affected)

## Status

Done 2026-11-13 (`Jdt.completeMembers`: in `acceptAST`, each project type a unit's names bind to (a type, a method's or a variable's declaring class, a variable's type), with its member types, supertypes and declaring class, is asked for its fields, methods and member types while the batch runs, each type once per batch. Default to reopen (the owner said take the smallest): the issue's third option, one place in `Jdt` and no tool changed, over parsing the declaring files with the batch (which needs a first pass to know them) and over reading the members from each declaring file's own parse by key (each of the ~40 callers changed). A type reached only through another binding (a method's return or parameter type) is not completed)

## Evidence

The Commons Lang stress run of `change_type_bound`, `docs/sessions/fix-gate-jdt-errors.md`; `docs/sessions/fix-declared-methods.md`
