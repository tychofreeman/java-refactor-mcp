# inline of a method in a record

- **Item:** issue #439 (the owner said yes, 2026-11-13), from the Java 17 stress run
  (`docs/sessions/stress-enum-switch.md`): `inline` of a record's method was refused, saying the
  file declares no type. Roadmap #186.
- **Date:** 2026-11-13
- **Branch:** fix-record-inline

## Plan

The change: `inline` finds a method in a top-level record as in a class. Checked first whether it
shares #431's cause (bindings after a batch): it does not; `Opened.topLevel` takes a
`TypeDeclaration` only, and `MemberMove.member` reads `getMethods()`/`getFields()`. Operations:
make the change easy while green (`MemberMove.member` over any `AbstractTypeDeclaration`), a
golden case from the issue's repro (red), then one `Opened` lookup that also takes a record, used
by `inline` only; and the refusal for a file with no class reworded.

## Notes

- read: `Opened.topLevel`, `Opened.notAClass`, `MemberMove.member` and `typeNamed` through
  `inspect` `names`; `InlineFunction.run`'s first 80 lines by range; callers of `topLevel` by grep.
- bulk: one `batch`: `change_type` of `member`'s parameter `t` to `AbstractTypeDeclaration` (one
  declaration, seven calls) and two `replace_statements` reading `bodyDeclarations()`; green.
- plan: golden `inline_function_record` from the issue's repro: red with the refusal of the issue.
- refusal: with the lookup fixed, the issue's own expected output (`relabel(relabel(label))`)
  is refused as "a call ... inside another call of it", as it is for a class, so the case uses
  `relabel(label).toUpperCase()`. The issue's "as a class's do" holds.
- one `batch`: `add_declaration` of `Opened.classOrRecord`, `replace_text` in `InlineFunction`
  (imports fixed). `replace_statements` of `notAClass`'s refusal; `extract_supertype_refused_record`'s
  `refused.txt` updated by hand (a resource file).
- hand: none in Java.

## Measured

Not measured: the session-log hook is not loaded in this session. From the notes: 5 tool calls
to Java (2 batches of 3 and 2 steps, `replace_statements`, 2 `replace_text`), no hand edit to Java.

## Assessment

Through the tools; the plan held. Records only and `inline` only, a default to reopen.

## Follow-ups

- Enums, and the other tools that call `Opened.topLevel` (`extract_supertype`, `change_type`,
  `move`, `encapsulate_field`, ...), still refuse a record or enum; switch each to
  `classOrRecord` (or a wider lookup) with a golden case when a task or the stress run needs it.
- The issue's nested call (`relabel(relabel(label))`) is refused for classes and records alike.
