# Split Signatures

- **Item:** roadmap order 25 (#32), "Split large classes": `Signatures` is
  818 lines, of which the nested `Family` (lines 203-497) and `Change`
  (503-636) are about 430. Chosen by the user (2026-11-03); the first real
  use of `move_type` (#52), whose trial move of `Family` passed the gate and
  the tests.
- **Date:** 2026-11-03
- **Branch:** split-signatures

## Plan

The change, at the highest level: `Signatures` keeps the three tools'
entry points and the helpers they share; the family search and the edit
set are their own top-level classes in the same package. No behavior
changes; the golden cases hold it.

Derivation, in order:

1. `move_type` `Signatures.Family`: `Family.java`; `Source`, `Decl`,
   `Call` and the helpers `Family` uses become package-private and are
   named in the result.
2. `move_type` `Signatures.Change`: `Change.java`; `Edit` and the
   helpers it uses widen the same way.
3. Read the result: whether `Signatures.Decl` and the like now read
   badly in the two new files, and whether a record that only one of them
   uses should move with it (another `move_type`).
4. `./mvnw verify`; the class sizes in the roadmap row.

## Notes

- plan: steps 1-2 as one batch of two `move_type` calls. `Family` widened
  `Source`, `Decl`, `Call`, `key`, `open`, `after`; `Change` widened
  `Edit` and `end`.
- plan: step 3 found `Edit` used only by `Change`: moved into it as a
  private record by a batch of `replace_declaration` (delete),
  `add_declaration` and `replace_text` of `Signatures.Edit` (5 places).
  No tool moves a type into another; the three composed to it.
- plan: top-level, `Family` and `Change` were too generic for the package
  (`Declarations` has its own `Change` record): renamed `MethodFamily` and
  `SignatureChange` with `rename`, files included.
- read: none of the moved bodies; the type and method outline, and the
  first lines of `Change` after the move.

## Measured

`bin/session-report` (`.claude/session-log/split-signatures.jsonl`):

- tool calls: 14
- through jrefactor: 4 refactoring steps, 3 edit steps
- refused by jrefactor: 0
- by hand: 2
  - Bash: cat > docs/sessions/split-signatures.md <<'EOF'
  - Bash: sed -i '63s/`Signatures.Family.of`/`MethodFamily.of` (was `Signatures.Family.of`)/; 205s/`Signatures...
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 1
  - Bash: sed -i '63s/`Signatures.Family.of`/`MethodFamily.of` (was `Signatures.Family.of`)/; 205s/`Signatures...: 22 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: sed -i 's/into `Signatures` (366, the entry/into `Signatures` (365, the entry/; s/`SignatureChange` ...
- reads: Bash java 3, inspect 1

Sizes: `Signatures` 818 lines before; now `Signatures` 365,
`MethodFamily` 327, `SignatureChange` 153.

## Assessment

- **Through the tools or around them:** all of it through java-refactor-mcp:
  two `move_type`, two `rename`, and three edits for `Edit`. By hand: the
  docs and this record.
- **Large writes:** none. About 430 lines moved, and none typed.
- **Did the plan hold?** Yes, with two steps added from reading the result:
  `Edit` into `SignatureChange`, and the two renames.
- **Reading:** outlines only.
- **One change that would have helped:** `move_type` into another type
  (`to: "Change"`), which three edits did here for a one-line record.

## Follow-ups

- `Source`, `Decl` and `Call` are now package-private records of
  `Signatures` used as `Signatures.Decl` in two other files; if a later
  change finds that awkward, `move_type` each.
- The rest of order 25: `Extract` and `Declarations`, `Tools`.
