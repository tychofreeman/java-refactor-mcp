# Overloads written alike can be named

- **Item:** roadmap #177 (order 170), issue #408, from the Commons Lang stress run
  (`docs/sessions/stress-new-options.md`, seed 11, `ExceptionUtils.throwUnchecked(T)`); the owner
  asked for the fix on 2026-11-13.
- **Date:** 2026-11-13
- **Branch:** fix-overload-names

## Plan

`Declarations.find` (syntax only) matches `name(types)` against each method's written types, so
`<T> T raise(T)` and `<T extends Throwable> T raise(T)` are both `raise(T)`; the refusal lists
and suggests that form. `ByLine.named` hands back the same written label, so `line` (#407) would
not help either. Options from the owner's brief: a bound syntax, erasure, or `line`. Erasure is
the smallest that makes every suggestion work and reuses a rule the code has (stub matching by
erased types, #107). Operations: add `erasedTypes` (syntactic erasure through the method's and
enclosing types' type parameters), a second match pass by erasure when the written one finds
nothing, a `methodLabel` that gives the erased form for a method written like a sibling, and use
it in the refusal, the result's label and `ByLine.named`. Goldens first: the issue's two calls by
erased name, the refused written name (the new wording), and `line` of the second overload.

## Notes

- plan: four goldens on the issue's `Ex` (with a caller of the `Object` overload): red as the
  issue says ("raise(T), raise(T) ... as raise(T)"; "no member raise with parameters (Object)").
- hand: one golden's `value=0` was read as a JSON number ("value must be a string"); quoted.
- refusal: the batch's `@@` sections were sent after a JSON argument with the code on stdin;
  the CLI reads sections only from the same text (`-` or `@file`): "content does not parse ... (@decls)".
  Resent as `bin/jrefactor -` with the JSON and sections on stdin; my misuse, not a tool gap.
- edit: one `batch` of four steps (`add_declaration` of four members to `Declarations`, three
  `replace_text`), checked at the end; imports chosen by the tool (JDT's `ArrayType`, `Type`, ...
  over `javax` and `java.lang.reflect`, by the package's other files). Green on every golden.
- hand: docs (contract, design decision and roadmap, session notes, this record), non-Java files.
- rebase: #407's branch was rebased three times onto a moving `main`; each conflict was the
  roadmap table's last row (both kept).

## Measured

Not measured: the session-log hook is not loaded in this session. Java changes: one batch call
(after one refused send), no hand edits to Java.

## Assessment

Through the tools for all Java. The plan held; the `ByLine` half was found by reading `ByLine`
before writing the test, which is why the `line` golden is on the second overload.

## Follow-ups

- `MemberMove.member` (`pull_up`, `push_down`, `move` of a member) has its own name parser and
  does not take erased names; one shared matcher would close it.
- Constructors written alike (`<T> Box(T)` and `<T extends Number> Box(T)`) go through
  `MethodFamily.constructorOf`, not checked here.
