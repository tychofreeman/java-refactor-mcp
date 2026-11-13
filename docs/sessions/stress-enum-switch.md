# Stress the enum switch forms on a Java 17 project

- **Item:** roadmap #181 (order 174). The owner asked (2026-11-13): the Commons Lang run
  (`docs/sessions/stress-new-options.md`) could not exercise `type_switch_to_method` over an enum
  (#392, #397, #399, #401), Lang being Java 8 source with no switch expressions or arrow switches.
  Pick a Java 14+ project and stress them there, with `add_parameter` `values` on its enums, and
  run every entry once at a modest count.
- **Date:** 2026-11-13
- **Branch:** stress-enum-switch

## Plan

No tool changes. Choose a project by the owner's criteria (public GitHub, single-module Maven,
Java 17+, many switch expressions or arrow switches over its own enums, small, ordinary license),
checking each candidate's source after a shallow clone. Check that the `type_switch_to_method_enum`
entry picks switch statements, `yield` blocks and member enums; widen it if not. Run it and
`add_parameter_enum_values` at a few seeds with refusals printed, then all entries once; read
every refusal whole, reproduce each bug in a scratch project with `bin/jrefactor`, one issue each.

## Notes

- `plan:` the entry already picks all three: `enumSwitch` takes any switch expression, and any
  switch statement of arrow cases, whose labels are simple names, whatever the case bodies
  (blocks with `yield` included) and wherever the enum is declared. Not widened. It parses without
  bindings, so it also offers switches over `int` and `char` with named constants: 306 of its 750
  calls on the project, which are refusals by design.
- `plan:` the run printed refusals but not what applied, so nothing showed which switches
  converted; `jrefactor.stress.refusals=true` now also prints each applied call's result.
- `bulk:` that change as one `bin/jrefactor batch` of two `replace_text` steps.
- `refusal:` the first send of the batch had each step's arguments at the step's top level;
  `batch` wants them under `args`. Resent.
- `hand:` candidate clones under `/home/claude/stress/cand` (deleted after), scanned with a grep
  script for arrow cases naming bare constants, `yield`, enums, modules and the release.
- `hand:` the variant copy of the project (below), by `sed` on the clone's copy.
- `hand:` each bug reproduced in a scratch Maven project under `/home/claude/stress/repro` with
  `bin/jrefactor`, written with heredocs; one manual `type_switch_to_method` on the variant
  (`BytecodeHelpers.convertToType`) to read a method-form result whole, then `undo`.
- `read:` `TypeSwitch.usesOnly` (lines 320-375) through `inspect`, for the wording of the
  "would mean another method" refusal.

## The project

Tried, in order (shallow clones, `grep` of the source):

| Candidate | Java files | Release | Arrow cases naming bare constants | Why not |
|---|---|---|---|---|
| TheAlgorithms/Java (MIT) | 1,649 | 21 | 0 | no enum arrow switches |
| datafaker-net/datafaker (Apache 2) | 745 | 17 | 48 in 19 files | few, and larger |
| FasterXML/jackson-core 3 (Apache 2) | 467 | 17 | 11 in 2 files | few |
| networknt/json-schema-validator (Apache 2) | 469 | 17 | 0 | none |
| jhipster/jhipster-lite (Apache 2) | 1,263 | 21 | 27 in 9 files | few, large |
| WebGoat/WebGoat (GPL 2) | 498 | 25 | 3 | none to speak of |
| mybatis/mybatis-3 (Apache 2) | 1,401 | 11 | 0 | Java 11, none |
| dmlloyd/jdk-classfile-backport | 0 | | | a personal fork, empty `main`; its upstream is the next |
| **smallrye/jdk-classfile-backport** (GPL 2 with the Classpath Exception) | 258 | 17 | **655 in 21 files**, 32 `yield` | chosen |

The backport (commit 2c4772b) is the JDK's `java.lang.classfile` API ported to Java 17: its
`Opcode` (about 200 constants with constructor arguments) and `TypeKind` enums are switched on
throughout `impl/BytecodeHelpers`, `CodeImpl`, `StackMapGenerator` and the verifier, and it has
member enums (`AccessFlag.Location`, `TypeAnnotation.TypePathComponent.Kind`,
`ClassPrinter.Verbosity`). A single module with one test file, a `smallrye-parent` parent, no
`./mvnw`: the server's classpath step runs `mvn` from the PATH (the proxy `settings.xml` first).
It is a named module (`module-info.java`), which turned up #438. Cloned to
`/home/claude/stress/jdk-classfile-backport`, outside every worktree.

## Runs

JDK 21, `jrefactor.stress.counts=true`, `jrefactor.stress.refusals=true`, each under the heavy
lock. Seeds 21-22 ran the jar of main 84dc88c; 23-25 the same code plus the printing change.
Every run left the clone clean (`git status`). About 1.5 s a call on 2 cores (Lang: 3.5 s).

| Run | Seed | Calls | Entries | Time | Applied | Refused | Failed |
|---|---|---|---|---|---|---|---|
| enum entries | 21 | 300 | `type_switch_to_method_enum`, `add_parameter_enum_values` | 9 min | 158 | 142 | 0 |
| enum entries | 22 | 600 | the same | 15 min | 306 | 294 | 0 |
| enum entries | 23 | 600 | the same | 13 min | 306 | 294 | 0 |
| enum entries, variant | 24 | 400 | the same | 12 min | 210 | 190 | 0 |
| all entries | 25 | 280 | all 28 | 10 min | 109 | 170 (62 by the gate) | 1 |

Of every applied call: no compile error added by a full count, the narrowed count equal to the
full one, and `undo` restoring every file and the first count. No entry of seeds 21-24 ever found
no target in 200 files; in seed 25, `function_to_method` found none three times.

The variant (seed 24) is a copy of the clone, `/home/claude/stress/jcb-blockthrow`, with each
bare `throw` arm of a switch (`default -> throw X;`) written as a block (`default -> { throw X; }`),
48 lines in 13 files: the tool refuses the first and takes the second (#435), so the variant shows
what those switches do once accepted.

### `add_parameter` with `values`

960 calls in the five runs, 960 applied, none refused or failed: every enum of the project, `Opcode`
with its 200 constants and three-argument constructor among them, gets a field, a constructor
parameter and each constant's argument, type-checked and undone.

### `type_switch_to_method` over an enum

Seeds 21-23, the clone as it is: 750 calls on 72 switches, 20 applied, 730 refused, 0 failed.

| Outcome | Calls | Reading |
|---|---|---|
| applied, method form | 20 | `TypeKind.descriptorOf`-style `switch (this)` inside the enum (line 162, the switch becoming `zqName()`) and `BytecodeHelpers` line 248 (a shared case copied into each constant) |
| refused: "each case must be a type pattern" | 304 | switches over `int` or `char` with named constants (`case TAG_UTF8 ->`); right to refuse, wrong words: #437 |
| refused: a case uses a local or a parameter | 175 | right: the method has no parameter for it ("does not add yet") |
| refused: the selector is a JDK enum (`DirectMethodHandleDesc.Kind`) | 72 | right |
| refused: a case calls an instance method of the switch's class (`pop()`, `i2d()` in `CodeBuilder`) | 60 | right; the wording "would mean another method" is loose, as the enum has no such method (not filed) |
| refused: a bare static hidden in the enum (`case ARRAY -> ARRAY;` in `TypePathComponent.of`, `Kind.ARRAY` would hide it) | 44 | right, and the fix (qualify) works |
| refused: a bare static of the enclosing class (`AccessFlag.findDefinition`, `case CLASS -> CLASS_FLAGS;`) | 33 | wrong: a member enum sees its enclosing class's statics: #436 |
| refused: `default -> throw ...` "is not an arrow case giving an expression" | 20 | wrong: #435 |
| refused: `as=field` on a case that is not a constant | 18 | right |
| refused: `int` selector with blocks, "each case must be an arrow case giving an expression" | 2 | right to refuse, wrong words: #437 |
| refused: a static of `RawBytecodeHelper` named bare through a static import the enum's file lacks | 2 | right |

Seed 24, the variant: 200 calls, 10 applied, 190 refused. Besides `TypeKind` line 162, the method
form applied to `BytecodeHelpers` lines 292, 320, 585, 619 and 647: switches over `Opcode` of 6 to
40 constants whose `default` throws, the enum's own method throwing with `this` (read whole for
`convertToType`, line 412, by hand). The other refusals were seeds 21-23's.

What the run exercised, by form: switch expressions applied (method form; a shared case; a
default that throws, in the variant). The field form was only refused: the project's constant
cases give `ConstantDescs.CD_*` or `TypeKind.REFERENCE`, which are not compile-time constants (an
enum constant as a field's value is a question for the owner, not a bug). Switch statements of
arrow cases (`CodeBuilder.conversion`, `ClassPrinterImpl`, `CodeStackTrackerImpl`,
`SignaturesImpl`), `yield` blocks (all over `int` here) and member enums (`AccessFlag.Location`,
`TypePathComponent.Kind`, `ClassPrinterImpl`'s style) were picked and each refused for a case using
a local, an instance method, or a static: right, except #436. No statement, `yield` block or member
enum was converted: the project's code has none that uses only constants.

### All entries (seed 25)

280 calls, 10 per entry: applied 109, refused 170, failed 1. The failure is `rename` of
`ClassHierarchyImpl.SYSTEM_STREAM_PROVIDER`: a JDT `NullPointerException` in
`Rename.checkMeanings`, issue #411, reproduced on main 0f641d8 and commented there; #432 fixed it on main while this record was written. Of the
refusals read whole:

- `move` of a member record whose fields the outer class reads (`RawBytecodeHelper.CodeRange`,
  `StackMapGenerator.RawExceptionCatch`): gated, fix text "Rename one of the clashing types": #440.
- `move` of a member of a sealed hierarchy with implicit permits (`ClassFile.Option`,
  `StackMapFrameInfo.SimpleVerificationTypeInfo`, `AbstractAttributeMapper.EnclosingMethodMapper`):
  gated, the same fix text: #441.
- `rename` of `CodeLocalsShifter` and `move` of `DiscontinuedInstruction.RetInstruction` and
  `ClassPrinter.ListNode`: gated on "Permitted type ... in an unnamed module should be declared in
  the same package", an error the project has before any edit, as the server checks a named module
  as the unnamed one: #438.
- `inline` of `CodeRelabelerImpl.relabel`, a record's method: "is a record ... declares none": #439.
- `extract_variable` `as=parameter` "has no member of with parameters (Annotation[])" for a
  varargs method: on main 0f641d8 (after #424) the refusal is "has varargs, which these tools do
  not change yet", a fair refusal. Not filed. Other `as=parameter` refusals on generic parameter
  types were #406, since fixed.
- The rest (pull_up and push_down gated on members the other class lacks, `change_type` bounds the
  bodies need, `remove_parameter` of a read parameter, `inline_variable` across an assignment,
  `convert_to_record`, `inline_function` overloads) read as right.

## Bugs found (issues, reproductions in each)

- #435: `type_switch_to_method` over an enum refuses a bare `throw` arm (`default -> throw ...`),
  which as a block passes; 20 calls on `BytecodeHelpers`.
- #436: a member enum is said not to see its enclosing class's static fields (`AccessFlag`).
- #437: over an `int` or a `char`, the refusal asks for type patterns.
- #438: a named module is checked as the unnamed module; sealed permits across packages are
  baseline errors, and a rename or move touching one is refused.
- #439: `inline` of a method in a record is refused, "declares none".
- #440: `move` of a member record whose fields the outer type reads.
- #441: `move` of a member of a sealed hierarchy with implicit permits.
- #411 (existing) hit again; fixed since by #432.

None is pinned by a golden case, as in the Lang run: a case pinning today's output would pin the
bug. No tool was changed here.

## Measured

Not measured: the session-log hook is not loaded here. From the notes: one batch of two
`replace_text` steps to `RefactoringStressTest` through `bin/jrefactor` (one refusal, resent),
docs by file edits.

## Assessment

- **Through the tools or around them:** the one Java change went through `bin/jrefactor`. The
  reproductions are scratch projects outside the repo.
- **Did the plan hold?** Yes. The entry needed no widening; printing what applied was the change
  the record needed.
- **What found the bugs:** reading whole refusals (#435-#441), none an exception; the all-entries
  run found more than the enum run, as on Lang, mostly in Java 17 shapes (records, sealed
  hierarchies, a module) that Lang does not have.
- **Limit:** the project gave the enum tool few applicable switches (31 conversions over seven
  sites). A project whose enum switches compute from constants alone would exercise the field
  form and the statement and `yield` method forms applied.

## Follow-ups

- The issues above.
- The field form, statements and `yield` blocks applied, on a project that has them.
- `type_switch_to_method_enum`'s picker could skip a switch whose labels are imported or
  declared `static final` primitives, to spend fewer calls on #437's refusals; left, since they
  check that refusal too.
