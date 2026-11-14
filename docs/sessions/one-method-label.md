# One label for a method

- **Item:** roadmap #493, the owner's choice of 2026-11-14 on the follow-ups of #501
  (`docs/sessions/one-label.md`), option (a): every method label as `Declarations.methodLabel`.
- **Date:** 2026-11-14
- **Branch:** one-method-label

## Plan

Red first: a `NamesTest` that labels every method of a nested class with overloads, generics,
varargs and a qualified parameter type, and sends each label back through `Declarations.find`,
`MemberMove.member` and a binding matcher (`Names.takes`, a stub). Then `Names.methodLabel` from
a binding: `typeLabel` of the class, then `memberLabel` (written types by `Extract.typeName`, or
erased when another method of the class reads alike); `Declarations.methodLabel` spaced the same
(`Names.typeText`). Fold `sourceLabel` and `signature` into it (`implement`, `Skeleton`,
`test_for`), match with `Names.takes`, and update the goldens whose text changes.

## Notes

- read: `inspect` `names` of the label helpers, `pattern` for their callers (16 files, about 25
  sites); only `Implement`, `Skeleton` and `TestFor` needed a change, the rest call
  `Names.methodLabel`.
- red: `NamesTest.everyMethodLabelNamesItsMethodBackInEveryTool` (erased labels; `takes` a stub
  declared by `add_declaration`).
- plan: the prefix is the class with its enclosing types (`Shop.Cart.add`), since `Declarations.find`
  and `MemberMove`'s callers resolve a type path from the top level; the owner alone (`Cart.add`) did
  not resolve for a member type.
- plan: a binding cannot say that source qualified a type (`java.util.Set<String>`); its label
  writes the simple name, and `name` compares with qualifiers dropped as a last tier.
- found: `Rename.refuseMethodClash` cut a label at its first dot, which left `Cart.add(...)` for a
  member type's method; now `Names.memberLabel`.
- hand: one edit of `NamesTest` by a Python replace (a `replace_text` would have done it); half of it
  (the `copy` guard) applied, the other half missed the formatted text and was redone with `replace_text`.
- red: `rename_method_refused_clash_member_type` ("already has a method Square.scaled(List)").
- goldens changed: `change_type_wildcard_refused_overload`, `batch_change_type_wildcard_refused_overload`,
  `change_type_bound_refused_override`, `create_file_declaration_generic`,
  `replace_declaration_generic_overload`.
- bulk: four batches (12 steps the largest), no single-call edits.

## Measured

Not measured: the session-log hook is not loaded here (the CLI log, `JREFACTOR_LOG`, is).
From the notes: one test edit by hand (undone and redone through the tools), no main code by hand.

## Assessment

Through the tools. Fewer pinned texts changed than the follow-up estimated (five): most goldens
name top-level classes with non-generic parameters, whose label did not change.

## Follow-ups

- `Comparable.compareTo(T)` is now how a stub list names a generic supertype's method (the
  declaration's written type), where it said `compareTo(Money)`; `implement` takes either.
