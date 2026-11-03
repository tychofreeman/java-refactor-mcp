# Move a nested type to its own file

- **Item:** the friction-log entry "nested class moved to its own file by
  script" (2026-11-03, roadmap order 25): `Extract`'s nested `Extraction`
  (660 lines) was copied out by a Python script and its helper calls
  qualified by regular expression, since no tool moves a type. The rest of
  order 25 (`Signatures`, `Declarations`, `Tools`) needs the same move.
- **Date:** 2026-11-03
- **Branch:** move-type
- **Choices (the user, 2026-11-03):** a new tool `move_type`, over an
  option on `rename` (easy to overlook, and a longer `rename` description)
  or on `replace_declaration` (which writes given content, so moving would
  mix meanings). Private members the move would put out of reach are
  widened to package-private and named in the result, over refusing with
  their names (two calls each time).

## Plan

The change, at the highest level: a static member type becomes a top-level
type of the same package, in its own file, with every reference in the
project still meaning it, and the type-check gate behind it.

Derivation, in order:

1. Red: golden `move_type`: `Shop.Line` (static, with Javadoc) uses
   `Shop`'s private static `label` unqualified; `Shop` reads `Line`'s
   private `price`; `Shop` refers to `Shop.Line`, a same-package file to
   `Shop.Line`, another package's file imports `com.example.Shop.Line`.
   Out: `Line.java` with the imports it uses, `Shop.label` qualified and
   both members widened, references made `Line`, the import
   `com.example.Line`. Refusals: an inner (non-static) class, a top-level
   type, a file that exists.
2. Green: register `move_type` (`Kind.REFACTORING`) whose handler, a
   `MoveType.run` that does not exist yet, the stubs declare.
3. Green: `MoveType.run`: find the type by `Outer.Inner` in `file`; refuse
   as above; collect, in every file that holds the name (one batch parse):
   qualified names and imports that resolve to the type (to `Inner`, and
   `pkg.Inner` in imports); in the moved text, unqualified uses of the
   outer type's static members and member types (qualified with `Outer.`);
   private members used across the new boundary (widened). Compose the new
   file (package, the outer file's imports the moved text uses, the type
   dedented with `static`, `private` and `protected` dropped), cut the type
   from the outer file, fix the outer file's imports, write through
   `Gate.apply`.
4. Refactor while green.

Not in this item: moving to another package; moving a top-level type into
another; moving members between types (`pull_up`/`push_down`, order 17).

## Notes

- refusal: the golden batch, step 5: `create_file` of `out/com/example/Shop.java`
  fixed its imports, though golden files are under no source root, and
  refused `Line` as ambiguous (`javax.sound.sampled.Line`, JFace). Resent
  without the two `out/` files that use `Line`.
- hand: those two golden `out/` files (`Shop.java`, 24 lines; `Receipt.java`)
  by heredoc and `sed`. `create_file` with `imports` would have added an
  import the expected output must not have (friction log).
- bulk: the golden cases (19 files, about 130 lines): new tests.
- refusal-free: registering `move_type` in `Tools` (three steps) declared
  the stub `MoveType.run`, as planned (step 2).
- bulk: `MoveType.java` (592 lines, by `create_file` over the stub): new
  behavior. `Rename.renameType`'s walk (files holding the name, one batch
  parse, a visitor per file) is repeated in its shape rather than shared;
  its helpers (`holding`, `typeLabel`, `joinAnd`) are private to `Rename`.
- read: `Rename.renameType` through `gate` (210 lines), `Lines.start`/`end`,
  `Signatures.label`, `Tools.rename`: to write the move like the type rename.
- test: the golden cases passed on the first full run but for a trailing
  newline in `result.txt`, fixed with `replace_text`.
- probe: `move_type` of `Signatures.Family` (327 lines) on this repo through
  the branch's jar: it qualified `Signatures.Decl`, `Signatures.Call` and
  `Signatures.Source`, widened those and three helpers, moved 19 imports,
  passed the gate and then every test. Reverted with `git restore` and
  `rm` (order 25's split is its own item; the server's `undo` lives in the
  process, which had ended).
- probe: Haiku, asked to give `Shop.Line` its own file, used `move_type`
  (8 turns, $0.0042).

## Measured

`bin/session-report` (`.claude/session-log/move-type.jsonl`), before this
section was written:

- tool calls: 25
- through jrefactor: 0 refactoring steps, 23 edit steps
- refused by jrefactor: 1
- by hand: 5
  - Bash: cd /home/cwfreeman/dev/ai-playground/java-refactor-mcp && cat > docs/sessions/move-type.md <<'EOF'
  - Bash: cd /home/cwfreeman/dev/ai-playground/java-refactor-mcp/src/test/resources/golden/move_type && cat > ...
  - Bash: cd /home/cwfreeman/dev/ai-playground/java-refactor-mcp && export JAVA_HOME=/usr/lib/jvm/java-21-open...
  - Bash: S=/tmp/claude-1000/-home-cwfreeman-dev-ai-playground-java-refactor-mcp/67b6617b-7d31-4ef2-baab-a80aa...
  - Bash: cat >> docs/sessions/move-type.md <<'EOF'
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 4
  - create_file src/test/resources/golden/move_type/in/com/example/Shop.java: 39 lines
  - Bash: cd /home/cwfreeman/dev/ai-playground/java-refactor-mcp/src/test/resources/golden/move_type && cat > ...: 49 lines
  - add_declaration src/main/java/io/github/tychofreeman/jrefactor/Tools.java: 28 lines
  - create_file src/main/java/io/github/tychofreeman/jrefactor/MoveType.java: 592 lines, replacing the file
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: R=$(bin/session-report 2>&1 | tail -30) && cat >> docs/sessions/move-type.md <<EOF
- reads: Bash java 1, inspect 1

## Assessment

- **Through the tools or around them:** all Java went through
  java-refactor-mcp, except two golden `out/` files written by heredoc after
  `create_file` refused them for an import it should not have looked for
  (friction log). The trial move's revert was `git restore`, since the
  server process that could `undo` it had ended.
- **Large writes:** `MoveType` and the golden cases, all new. No existing
  code was typed out again.
- **Did the plan hold?** Yes: red golden cases, the registered tool and its
  declared stub, then `MoveType`; step 4's refactor found nothing worth
  sharing with `Rename` without making `Rename`'s helpers package-private.
- **Reading:** `Rename.renameType`, to write the same walk.
- **One change that would have helped:** `create_file` leaving files under
  no source root alone (friction log, 2026-11-03).

## Follow-ups

- Split `Signatures` (order 25) with `move_type`: `Family` is a tried
  move; `Change` is next.
- A file in another package that now names the type only through `import
  a.Inner` keeps an `import a.Outer` it may no longer use.
- Not handled: moving to another package; a type reached through a subclass
  of the outer type (`Sub.Inner`) in another file; static imports of the
  moved type's members (`import static a.Outer.Inner.X`). The gate refuses
  what these break.
- `Rename` and `MoveType` each walk "the files that hold the name"; one
  helper could serve both (with order 33's folding of `Rename`'s walks).
