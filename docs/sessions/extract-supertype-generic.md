# extract_supertype from a generic class: an interface

- **Item:** roadmap order 137 (#144): increment 1 of `docs/sessions/generics-sketch.md`,
  `extract_supertype` with `kind=interface` from a generic class. The owner chose the sketch's
  recommendations on 2026-11-13 (slice 2, option (a)).
- **Date:** 2026-11-13
- **Branch:** extract-supertype-generic

## Plan

Written before reading `ExtractSupertype`'s bodies, from the sketch.

The change: the new interface declares the class's type parameters its signatures use, and
those their bounds need, in the class's order, each as the class writes it; the class
implements it with their names.

1. Goldens first, red: `extract_supertype_generic_interface`, `_generic_interface_subset`,
   `_generic_interface_none`, `_generic_bound`, `_generic_method` (expected green already),
   `extract_supertype_refused_type_parameter_name`, `_refused_private_bound`.
2. Make it easy: find where the header of the new file and the text given to `AddSupertype`
   are built; if they are inline strings, extract them so the type parameters are one value.
3. The behavior change: drop the "is generic" precondition; compute the used parameters by
   binding over the extracted signatures, close over bounds; write `<...>` in both places.
4. Two refusals that name the fix: a supertype named as a type parameter; a bound that uses a
   type the new file cannot name.
5. Docs: the contract's section and refusals, the roadmap row, session notes, the sketch.

## Notes

- read: `ExtractSupertype` through `inspect` (the outline, then `run` whole, 130 lines), and
  `Extraction.typeParameters`, `ExtractType.unusable`, `AddSupertype.run`, `Opened`,
  `PullUp.run`, `Nodes.all`, `Names.typeLabel` by `names`.
- plan: step 2 was not needed: the header and the `AddSupertype` text were each one expression.
- bulk: seven golden cases written as plain files (a shell script); `_generic_method` passed
  before any code changed, the other six failed on the old refusal, as planned.
- refusal: the first `batch` put each step's arguments beside `tool`: "replace_text needs
  file". Resent with `args` (friction log).
- One batch of eight steps, check `"end"`: five `replace_text` in `run`, three
  `add_declaration` (`Generics`, `typeParameter`, `generics`). Imports were added by the tools.
- The `_generic_bound` fixture had a field and a method both named `values`; `members` could
  not tell them apart (an existing refusal). The fixture's field was renamed, not the tool.
- An eighth golden, `extract_supertype_refused_generic_class`, pins the refusal `kind=class`
  keeps until roadmap #145: it gives the steps (`create_file`, `add_supertype`, `pull_up`).
- `ExtractType.unusable` was not reused for the bound's check: it refuses a type variable,
  which a bound may name. The private-type walk is repeated in `generics` (six lines).
- Docs through one `batch` of `replace_text` (the friction log with `append`); the sketch
  copied with `cp`.
- hand: none in `src/main/java`.

## Measured

Not measured: the session-log hook is not loaded in this container. From the notes: 2 batches
(one refused once), 17 steps, 5 `inspect` calls; no hand edit of Java.

## Assessment

Through the tools. The plan held, less its step 2. The change is one method and three call
sites; the sketch's reading of the code ("`_generic_method` should already work") was right.
What would have helped: a batch refusal that names `args` (friction log, 2026-11-13).

## Follow-ups

- `kind=class` from a generic class: roadmap #145, next.
- A signature or a bound that names a package-private member type by its simple name (`Key`
  for `Repo.Key`) is copied as written and refused by the gate, for a non-generic class too;
  writing it qualified in the new file would lift that.
- The other generic refusals (`move` of a member, `subclass_to_strategy`, `inline`,
  `extract_type`) and a shared wording: the sketch's "Risks".
