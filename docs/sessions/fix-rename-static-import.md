# rename beside a single static import

- **Item:** roadmap #175 (order 168), issue #411, from the Commons Lang stress run
  (`docs/sessions/stress-new-options.md`, seed 13): `rename` errored with a JDT
  NullPointerException when a file its meaning check re-parses has a single static import of a
  project method.
- **Date:** 2026-11-13
- **Branch:** fix-rename-static-import

## Plan

Find why the re-parse cannot resolve the import, and fix the parse, not `Rename`: every tool walks
`SimpleName`s and asks their bindings, an import's name among them. Then the issue's
reproduction as a golden (red), the fix, green.

## Notes

- `read:` `Rename.checkMeanings` and `meanings` through `inspect`: `checkMeanings` re-parses only
  the renamed files (`Jdt.parseAll`), then resolves every `SimpleName`.
- `read:` the cause, by a probe outside the repo (plain `ASTParser`, as `Jdt.parser` sets it up):
  the import's binding resolves inside the batch's `acceptAST` and throws after the batch; with
  `Util.java` in the batch it resolves after too. JDT's DOM looks up an import's binding only
  when asked (`CompilationUnitScope.getImport`), and a single static import looks through every
  method of the imported type (`getMethods()`); a type the batch read from the source path has
  only the members the batch needed resolved (`Util`'s default constructor was not), and its scope
  is freed when the batch ends. An on-demand static import and a static field import resolve.
  The narrowed type check was not involved: it only reads problems. A single-file `Jdt.parse` does
  not throw.
- `red:` `rename_method_beside_static_import` threw the issue's NullPointerException.
  `rename_refused_static_import_captured` (rename `other` to `twice`, capturing `twice(3)`) first
  sent `other()` with no parameter, refused by the gate for arity; given `other(int x)`, it pins
  the meaning check's refusal through a static import.
- `bulk:` the fix as one `batch` (two `replace_text`, `staticImported`, the pattern field), check
  "end"; imports added by the tool. Docs through `replace_text`.
- `stress:` see Measured.

## Measured

Not measured: the session-log hook is not loaded in this session. From the notes: Java read through
`inspect` only; one batch wrote the Java; no hand edit to Java.

`RefactoringStressTest` on Commons Lang 9368465, seed 13, 40 calls of `rename` only (the issue's
run was 150 calls of every entry; this one aims at the tool that failed): 40 applied, 0 refused,
0 failed, no "Could not retrieve declared methods" line, the clone clean after. The same run on
the old jar was not made, so it does not show how many of the 40 reached a static import.

## Assessment

Through the tools. The plan held: the fix is in the parse (`Jdt.parseAll` parses, with its files,
the files that declare a type they import a single static member of; only the asked-for units go
to the caller), so every tool that resolves an import's name gets it, not only `rename`. The extra
files cost a little in the narrowed check, one file per distinct project type statically imported.

## Follow-ups

- The same freed scope makes `ITypeBinding.getDeclaredMethods()` of a project type outside the
  batch return no methods (JDT logs "Could not retrieve declared methods"); the stress runs print
  that line often. A tool that asks it may decide on an empty list. Issue #431.
