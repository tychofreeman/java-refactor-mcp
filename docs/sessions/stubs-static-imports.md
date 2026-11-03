# Static imports before stubs

- **Item:** friction-log entries "stubs declared a local assertEquals for a
  missing static import" (#9) and "a stub assertFalse again" (#45): twice
  in one day, an unqualified call to a method that a class the file already
  imports statically has became a stub method in the test class. Chosen
  (2026-11-03) as work beside another session's ASTParser item, since
  `Imports` is not one of its files.
- **Date:** 2026-11-03
- **Branch:** stubs-static-imports

## Plan

The change, at the highest level: an edit's unqualified call that resolves
to nothing is first looked for among the static methods of the classes the
file already imports statically, and imported from the one that has it, as
an unresolved type name is imported today; only what is still unresolved
becomes a stub.

Derivation, in order:

1. Red: golden `imports_static_method`: `add_declaration` of a test that
   calls `isFalse` (the project's `Check`, which the file imports `isTrue`
   from) and `min` (`java.lang.Math`, which it imports `max` from). Today
   it declares two stubs.
2. Green: in `Imports.fix`, collect unqualified method calls with no
   binding that were not unresolved before the edit; for each, the classes
   of the file's single static imports whose static methods include the
   name; one class gives `import static C.name;`, placed in order among the
   static imports. Several classes or none: left to the stubs, as now.
3. Refactor while green: share the insertion and the "look again until no
   more" loop with the type imports where they repeat.

Not in this item: on-demand static imports of a class that lacks the name
(nothing to find); static imports of fields.

## Notes

- bulk: the golden case (6 files, about 50 lines, all new).
- refusal: the code batch, step 4: `MethodInvocation` could be JDT DOM or
  JUnit's engine class. Resent with `imports` for it and the other DOM
  names (`Modifier` would have been the next: `java.lang.reflect` too).
- read: `Imports.fix`, `toImport`, `insertionPoint`, `unresolved` (about
  190 lines), to write the static pass beside the type pass.
- bulk: `staticImports` and `uncalled` (58 lines) and `insertionPoint`
  (26 lines, sent whole to change a few lines): new behavior, and existing
  code sent again where `replace_text` of its loop would have been smaller.
- plan: step 3 (sharing the loop) was not needed: the static pass runs once,
  and a static call inside a lambda that resolves only after a type import
  is not handled (follow-up).
- probe: the branch's jar over stdin, on this repo: `add_declaration` of a
  test calling `assertNotNull` in `SessionReportTest` (which imports only
  `assertEquals` and `assertTrue`) gave "imported static
  org.junit.jupiter.api.Assertions.assertNotNull" and no stub; `undo` in the
  same process restored the file.

## Measured

`bin/session-report` (`.claude/session-log/stubs-static-imports.jsonl`),
before this section was written:

- tool calls: 16
- through jrefactor: 0 refactoring steps, 10 edit steps
- refused by jrefactor: 1
- by hand: 2
  - Bash: cd /home/cwfreeman/dev/ai-playground/java-refactor-mcp && cat >> docs/sessions/README.md /dev/null; ...
  - Bash: python3 - <<'EOF'
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 2
  - replace_declaration src/main/java/io/github/tychofreeman/jrefactor/Imports.java: 26 lines
  - add_declaration src/main/java/io/github/tychofreeman/jrefactor/Imports.java: 58 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: R=$(bin/session-report 2>&1 | tail -30) && cat >> docs/sessions/stubs-static-imports.md <<EOF
- reads: inspect 3

## Assessment

- **Through the tools or around them:** all Java and the golden case went
  through java-refactor-mcp; by hand were this record and the docs.
- **Large writes:** new behavior and the golden case, except
  `insertionPoint`, sent whole to change its loop's test.
- **Did the plan hold?** Steps 1-2 as planned; step 3 was not needed.
- **Reading:** `Imports`' pass for types, to write the static one beside it.
- **One change that would have helped:** prefer the JDT DOM class for an
  ambiguous name in a file that already imports from `org.eclipse.jdt.core.dom`
  (the `MethodInvocation` refusal; the friction log's 2026-11-03 idea of
  preferring what the file's other imports suggest).

## Follow-ups

- A static call inside a lambda that resolves only once a type import does
  is not found: the static pass runs once, not in the "look again" loop.
- Two classes with the method: left to the stubs; could be refused with
  both, as an ambiguous type name is.
- Unused static imports are not removed.
