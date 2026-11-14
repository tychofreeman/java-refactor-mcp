# A named module checked as the unnamed one (#438)

- **Item:** issue #438, from the jdk-classfile-backport stress run (`docs/sessions/stress-enum-switch.md`);
  the owner said yes to fixing it. Roadmap #180.
- **Date:** 2026-11-13
- **Branch:** named-module

## Plan

The issue's part (1), check the project as its module, if JDT's public API allows it: look at
`Jdt.parser` (`setEnvironment`) and the resolver with `javap`. Otherwise the smallest fix that
removes the refusal: drop the one error the unnamed reading adds, in the gate's count, so the full
and the narrowed count agree. Golden cases: `rename` and `move` of a sealed type that permits a
class of another package, in a named module (pass) and without `module-info.java` (still refused).

## Notes

- read: `Jdt.parser`, `TypeCheck` whole (inspect), `SourceProject` outline.
- read: `javap -c` of `ASTParser.getClasspath` (classpath and source path only, through
  `Main.processPathEntries`), `CompilationUnitResolver` (units built with the batch
  `CompilationUnit(char[], String, String)`, which passes a null module). A spike parsing the repro
  with and without `module-info.java` in the batch: the error either way. Part (1) needs JDT
  internals; took the filter (Decisions in `docs/design.md`, a default to reopen).
- plan: the error is `IProblem.SealedPermittedTypeOutsideOfPackage` (1859); javac's rule in a named
  module is "same module", which a single-module project always meets.
- red: the three goldens by `bin/jrefactor` on copies (the named `rename` and `move` refused with
  the issue's text); the `move` golden's `module-info.java` exports nothing, since moving `Shape`
  empties `a` and the check cannot see an empty exported package.
- refusal: a first `batch` sent with check outside the JSON ("Arguments are key=value pairs, or one
  JSON object"), then the steps' arguments at top level ("add_declaration needs file"; already in
  the friction log); both resent as the refusals said.
- tools: `SourceProject.namedModule`, `TypeCheck.counts` and the two call-site changes in one
  `batch`; the guard (`TypeCheck.namedModule`, `FORMAT` 4) in a second; the unit test by
  `add_declaration`.
- red: a `create_file` golden adding `module-info.java` (to show the narrowed count keeping a
  dropped error) was refused, "module-info.java does not parse" (friction log); written as
  `TypeCheckTest.aModuleInfoAddedOrRemovedRecountsEveryFile` instead, red, then green.
- hand: golden resource files and docs (plain files).
- check: on a copy of the backport's `src/main/java` (no build run), `rename` of
  `components.CodeLocalsShifter` now passes (4 files, 15 occurrences).

## Measured

Not measured: the session-log hook is not loaded here. Java written: 2 batches and 1
`add_declaration` through `bin/jrefactor`, none by hand.

## Assessment

Through the tools for every Java change. The plan held, at its fallback: the module path is not
reachable through `ASTParser`. The filter is narrow on purpose: the other unnamed-module
differences (`requires`, `exports`) only make the check more lenient, not refuse.

## Follow-ups

- `create_file` (and any edit) of `module-info.java`: the syntax parse needs the unit name.
- `module-info.java`'s own errors are noise ("The package a does not exist or is empty" for every
  export), so an edit that empties an exported package is not caught.
- The issue's part (2), an error carried by a rename or move not counted as new, and the
  "Choose a name that does not clash" fix text for errors that are not clashes.
