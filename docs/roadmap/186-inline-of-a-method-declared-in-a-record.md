---
order: 179
item: 186
status: Done
---

## Item

`inline` of a method declared in a record was refused as if the file declared no type ("Send the name of one: ... declares none"; issue #439, from the Java 17 stress run)

## Status

Done 2026-11-13 (`Opened.classOrRecord`, which `InlineFunction` takes its type from: a top-level record, else `topLevel`; `MemberMove.member` reads any `AbstractTypeDeclaration`'s body declarations. `Opened.notAClass` says "Its file ... declares no class or interface to send in its place" when there is none. Defaults to reopen (the owner said take the smallest): records only and `inline` only, over enums too and over every tool that calls `Opened.topLevel`; the other tools keep the refusal, reworded)

## Evidence

The enum switch stress run, `docs/sessions/stress-enum-switch.md`; `docs/sessions/fix-record-inline.md`
