# Names and tables in the signature tools (#530, part 1)

- **Item:** issue #530 (code review, group 5 of #532): naming and primitive obsession in the
  signature tools. This branch: `Build.Place`, `PrimitiveUses`, `Placement.rank`, the type tables.
- **Date:** 2026-11-14
- **Branch:** signature-naming

## Plan

Behavior-preserving tidying, all through the tools:

1. `rename` `Build.Place` (a private record that shadows the top-level `Place`) to `Build.Site`.
2. `rename` `PrimitiveUses` (easy to confuse with `Primitives`, the edit primitives) to
   `TypeDecidedUses`, after what it finds: the uses whose result the type decides.
3. `Placement.rank` as an enum: `add_declaration` of `Placement.Rank`, then one batch:
   `change_signature` of `rank`'s return type, `change_type` of the `rank` parameters of `place`
   and `MemberMove.addTo`, `replace_declaration` of the bodies that compare, and the `0` in
   `Introduce` and `comparingInt` in `Declarations` by `replace_expression`.
4. One `JavaTypes` table: `create_file`, then `move` `TypeParameters.BOXED`, `AddResult.zero` and
   the two sets of `PrimitiveUses` into it; `TestFor`'s placeholder switch extracted
   (`extract_variable`, `extract_function`, `inline`) and moved there too.

## Notes

- rename: `Build.Place` to `Site` (and its local), `PrimitiveUses` to `TypeDecidedUses` (file
  renamed with it), `Signatures.checkPrimitiveUses` to `checkTypeDecidedUses`: three calls.
- refusal: `change_type` of `Placement.rank`'s return type, int to `Rank`: first "used as a method
  reference ... fixes its parameters" (`Comparator.comparingInt(Placement::rank)`; a return type,
  not a parameter, and `comparing(Placement::rank)` would still fit), then, with a lambda in its
  place, the primitive-use rule at `place(..., Placement.rank(d))` ("could change ... the overload
  chosen"): the argument's parameter is the next change, so each order refuses. Done as one batch
  of edits (`replace_declaration`, `replace_text`, `replace_expression`). Friction-log entry.
- refusal: `move` of `TypeParameters.BOXED` (initializer calls `Map.of`). hand: `add_declaration`
  in `JavaTypes`, `replace_text` of the uses, then `replace_declaration` deleting it (the delete
  refused while uses remained: reordered in the batch). Same for `PrimitiveUses`' two sets, which
  became `JavaTypes.isPrimitive` and `isBox`.
- move: `AddResult.zero` to `JavaTypes` (one call).
- `TestFor.placeholders`' switch: `extract_variable` twice, `extract_function` `placeholder`,
  `inline` of two locals, `replace_declaration` to drop the parentheses `inline` wrote around the
  switch, `move` to `JavaTypes`.

## Measured

Not measured (no session-log hook in this container). From the notes: 3 renames, 2 moves, 2
`extract_variable`, 1 `extract_function`, 3 `inline`; edits: 3 batches (the `Rank` change, the
boxing table, the placeholder method) and 1 `replace_text`; 0 Java by hand outside the tools.

## Assessment

Through the tools. The plan held except where the tools refused: the `int` to enum change (the
primitive-use rule, both orders) and the move of a field whose initializer calls `Map.of`, both done
as batches of edits and logged in the friction log. Docs: `docs/design.md` and
`docs/tool-contract.md` name `TypeDecidedUses`; session notes say where the tables are.

## Follow-ups

- The rest of #530 (`ParameterValue.occurrences`, its `Swap`, `ChangeSignature.names`): a second PR.
