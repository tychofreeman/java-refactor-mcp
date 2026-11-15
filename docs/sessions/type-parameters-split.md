# TypeParameters.toClass, toMethod and bound in parts (#528, item 5, second half)

- **Item:** issue #528, item 5, the half that waited for #375: `TypeParameters.toClass` (214
  lines), `toMethod` (268) and `bound` (195).
- **Date:** 2026-11-15
- **Branch:** type-parameters-split

## Plan

`extract_function` of the blocks that take few of each method's locals, bottom first in each.

## Notes

- toClass: `fix` (4 parameters), `position` (5), `refuseHidden` (5).
- toMethod: `explicitTypeArguments` (3), `units` (1), `texts` (1), `refuseHiddenInFamily` (2).
- bound: `keptCalls` (5; it takes the count's start, 0, as the extraction found it), `casts` (3).
- Not extracted: each method's main loop over the uses (toClass's reads 10 locals and updates 5
  counters), the verification loop of toMethod (7 and two counts), bound's two branches (8 and
  three results), and the result messages (10 values each).

## Measured

Not measured (no session-log hook). 9 `extract_function`; no Java by hand.

## Assessment

Through refactorings. `toClass` 214 -> about 170 lines, `toMethod` 268 -> about 227, `bound` 195
-> about 160. What remains is loops over shared state; shortening them would want a record of
each change's context first (as `MovedType` was for `MoveType`).

## Follow-ups

- A context record per form (class, method, bound), then the loops as methods.
