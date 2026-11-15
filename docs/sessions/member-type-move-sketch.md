# Moving a member type into another class: options (#528, item 2)

- **Asked by:** the owner, 2026-11-15, after #658: item 2 of #528 wants the records `Source`,
  `Decl`, `Call`, `Lambda` and `Reference` moved from `Signatures` to `MethodFamily`, and no tool
  moves a member type into another class.
- **Status:** options for the owner; nothing is built.
- **Roadmap:** item 513.

## What happens today

- `move` with `name` `Signatures.Decl` and `to` `MethodFamily` routes to the member route
  (`MoveMember`), which looks for a method or field and refuses: "Signatures declares no method or
  field Decl". The refusal does not say what does exist.
- `move` with `name` alone (`Signatures.Decl`) runs `MoveType`: the member type becomes a
  top-level type in its own file, every reference is rewritten (`Signatures.Decl` becomes `Decl`,
  an import where the package differs), what it reached through `Signatures` unqualified is
  qualified, private members used across the new boundary are widened, a nested record's private
  fields read across it become accessor calls (#192), a sealed type gets an explicit `permits`
  (#190). The context travels as a `MovedType`.
- Nothing makes a top-level type a member of a class, so moving it on is the missing half.

## The case at hand

- Five records, all implicitly static. `Decl`, `Call`, `Lambda` and `Reference` name `Source`;
  they are named `Signatures.Decl` and so on in about 100 lines of seven files (`ChangeSignature`,
  `ExtractType`, `FunctionToMethod`, `MethodFamily`, `ParameterValue`, `SignatureChange`,
  `ThrowsCallers`), and bare inside `Signatures`.
- Moved, they would be named `MethodFamily.Decl` outside `MethodFamily` and `Decl` inside it
  (inside `Signatures` too, they become `MethodFamily.Decl`).
- The same package, so no import changes; no private member of `Signatures` is used by them
  (to be checked by the tool, not assumed).

## What a member-type move must handle

1. **References.** Every use, by binding, written as code at its place names the type: the
   simple name inside the target (and its member types), `Target.Inner` elsewhere, an import
   changed where a file imported `Outer.Inner` (single-type or static import), qualified names
   in Javadoc `{@link}`s left to a note.
2. **Static or inner.** Records, enums and interfaces are implicitly static. A static class moves
   as is. An inner class (no `static`) has an enclosing instance: refuse when its body uses the
   outer instance (`Outer.this`, an outer field or method unqualified) or when it is created as
   `outer.new Inner()`; otherwise it may move, keeping its form only if the target's instances
   are what creates it, so the safe default is to refuse inner classes.
3. **What it reached through the old outer type.** Static members and member types of `Outer`
   (and of its supertypes and enclosing types) named unqualified inside the moved type must be
   qualified (`MoveType.qualifyAndWiden` and `qualifier` already do this for a move to its own
   file).
4. **Private access across both boundaries.** The old outer type's private members the moved type
   uses, and the moved type's private members the old outer type uses (a nested type's privates
   are visible to its whole top-level type), become package-private, as `MoveType` does, or
   accessor calls for a record's fields (#192). Inside the target, nothing new is needed.
5. **Name clashes in the target.** A member type, type parameter or type the target already
   names with that simple name; a member type of the moved type that would hide a name the target
   uses; the moved type's own uses of a name the target declares (a field `key` of the target
   hiding nothing in a record, but an unqualified `key(...)` call inside the moved type would bind
   to the target's static `key` once nested there). `NameCheck` judges the moved text's meanings
   by binding key, as it does for `move` of a method.
6. **Visibility and packages.** Kept as written when the target is in the same package; a target
   in another package needs the type public when used from outside it, and the target class
   visible where the type is used. The target may be an interface (members implicitly
   `public static`), a record or an enum (after its constants).
7. **Placement.** By kind, through `MemberMove.addTo` / `Placement`, indented by `Indent.restep`
   (the owner's "layout is the tool's" rule).
8. **Sealed hierarchies and generics.** A member type permitted implicitly by a sealed outer type
   needs `permits` (#190); a member type that uses the outer type's type parameters cannot move
   to a class that does not declare them (refuse).

## Options

**(A) `to` on `move` of a member type: one call, `move name=Signatures.Decl to=MethodFamily`.**
`Tools.move` routes a `to` whose `name` is a member type to a new `MoveType` route instead of
`MoveMember`. It reuses `MoveType`'s reference walk, qualification, widening, accessor rewrite
and permits (they already take a `MovedType`), and adds the attach half: placement in the target
and references written relative to the target.
- For: one step, the shape the owner chose for `move` (one tool for types, members and files);
  no intermediate top-level type whose simple name could clash with an import elsewhere (`Call`,
  `Source` are common names); the refusal that exists today becomes the entry point.
- Against: the largest piece of work: about 300 to 500 lines and a dozen goldens (static record,
  inner class refused, private across both boundaries, clash in the target, other package,
  interface target, sealed, generic refused) plus probes for name capture. `MoveType.run` (194
  lines) would want splitting first.

**(B) A composition of two moves: to its own file (exists), then `move` of a top-level type with
`to` a class ("nest").** The new operation is only the second half: a top-level type of a file
becomes a static member type of a class, its file deleted, references rewritten to
`Target.Inner`. The member-type move is then a derivation (two calls), recorded in
`docs/derivations.md`.
- For: each half is simpler and testable alone; nesting a top-level type is useful on its own
  (grouping a package's small records under the class that owns them); the existing un-nest is
  untouched.
- Against: two calls and an intermediate state: the top-level `Call` or `Source` must type-check
  between them (a name clash with a type another file imports on demand would refuse the first
  step); private-access handling is split across the two steps (widened by the first, which the
  second cannot narrow again); a model must know the composition.

**(C) Leave the records in `Signatures`.** Close item 2 as done in part (#658 moved the methods)
and record the gap.
- For: no cost; the records are an implementation detail of the signature tools, and their
  qualified name (`Signatures.Decl`) reads fine.
- Against: item 2 stays half done; the next session that wants a member type moved meets the same
  refusal; by hand it is a 100-line write across seven files.

## Recommendation

(A), built in slices, each its own PR: first a static member type (record, enum, interface,
static class) to a class of the same package, refusing inner classes, other packages and
generics; then private access across both boundaries; then other packages. The first slice moves
the five records. The refusal for a member type with `to` today could name `move` without `to`
in the meantime.

## Open questions

1. (A), (B) or (C)? Recommended: (A), sliced.
2. An inner class (not static): refuse, or move it when its body does not use the outer instance
   and add `static`? Recommended: refuse in the first slice; adding `static` changes what the
   class may later do, so it is the model's step.
3. References: write `MethodFamily.Decl` everywhere outside the target (as `Signatures.Decl` was
   written), or import the member type (`import ...MethodFamily.Decl;`) and write `Decl`?
   Recommended: the qualified form, which keeps each use's shape and needs no import changes.
