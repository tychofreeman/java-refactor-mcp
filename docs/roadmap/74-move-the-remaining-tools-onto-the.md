---
order: 67
item: 74
status: Closed
---

## Item

Move the remaining tools onto the refactoring shape: `Rename` (`Query`), `MoveType`, `Extract`, `Declarations` (`find`) and the signature tools (`MethodFamily.of`) resolve targets through `Opened`; the up-front checks still written as `if`/`throw` in `InlineVariable`, `ExtractVariable`, `ChangeType` and `EncapsulateField` become `Precondition`s

## Status

Closed (2026-11-04, the user's choice): `Declarations` parses for syntax only on purpose (a binding parse would slow every edit), `Extract` checks the range against the text before it parses, and `MoveType`, `Rename` and the signature tools read many files (`Holding`, `parseAll`) where `Opened` is one; the twelve tools on `Opened` stay

## Evidence

Follow-ups of #71 (`docs/sessions/refactoring-shape.md`). No behavior change; the goldens are the check
