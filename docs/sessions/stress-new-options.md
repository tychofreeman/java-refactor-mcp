# Stress the options merged on 2026-11-13

- **Item:** roadmap #170 (order 163). The owner asked (2026-11-13) for the stress runs skipped
  that day: the session notes ask for `RefactoringStressTest` on Commons Lang before merging
  `add_parameter` with `type_parameter` and `members` (#390), `change_type` bounds (#400), and
  after the enum method form of `type_switch_to_method` (#397). Those PRs merged without it, as
  there was no Commons Lang checkout and the test did not pick the new options. Also
  `NarrowCheckStressTest` once, since the day's PRs changed what the gate checks (`CallTargets`).
- **Date:** 2026-11-13
- **Branch:** stress-new-options

## Plan

The change: `RefactoringStressTest` draws targets for each new option, as it does for the older
tools (a random file, a random fitting node, the tool's arguments built from it), and the run
counts applied and refused by reason. Test-only, so no red step.

1. Ten entries in `TOOLS`, each its tool with an option, mapped to the tool in the `real` switch
   as `move_type_package` is: `add_parameter_type_class` (a type its instance members declare
   as `value`, half the time one or two of those members as `members`),
   `add_parameter_type_method` (a type a parameter names), `add_parameter_enum_values` (each
   constant its index; a third of the time one left to `value`), `remove_parameter_value` (from
   a call: an argument that reads other arguments that are names, written with their
   parameters; the declaration found by name and arity in a project index), `change_type_bound`
   (a type parameter of a class or a method, one of five bounds, `Object` among them),
   `change_type_wildcard` (a type argument of a parameter's type made `? extends` or `? super`),
   `type_switch_to_method_enum` (a switch expression or arrow switch whose cases name constants;
   `as` field, method or none), `extract_variable_parameter_all` (an infix of parameters,
   constants and literals that reads a parameter, one occurring twice most of the time),
   `extract_variable_parameter_call` (a call on a parameter or a class), and
   `extract_supertype_generic` (a generic top-level class, as an interface or a class).
2. `jrefactor.stress.tools` picks entries, so a run can spend its calls on the new ones;
   `jrefactor.stress.refusals` prints each refusal whole, to tell a fair refusal from a bug; and
   the run prints how often an entry found no target in 200 files.
3. Run on a Commons Lang clone (outside the worktree), then `NarrowCheckStressTest` once; each
   failure reproduced, logged in the friction log and filed as an issue. No tool is fixed here.

## Notes

- `refusal:` the first batch: the record component `methods` hid the static `methods(unit)` in
  `Hierarchy.of`; renamed `declared` and resent.
- `refusal:` a `replace_text` of the class's Javadoc after `spotless:apply` had reflowed it;
  copied from the file and resent.
- `hand:` `-Werror` on a raw `i.arguments().stream()`; fixed through `replace_text`.
- `hand:` the Commons Lang clone (`/home/claude/stress/commons-lang`, commit 9368465, outside the
  worktree) and its classpath: Maven's `dependency:build-classpath` as `MavenBuild.classpath` runs
  it, with `mvn` on the PATH (Lang has no `./mvnw`); 11 s, 23 jars.
- `plan:` the first trial printed refusals cut at 60 characters, too short to tell a fair refusal
  from a wrong one, so `jrefactor.stress.refusals` was added before the real runs.
- `read:` `Introduce` lines 145-215 and `Signatures.label`, to find why `as=parameter` could not
  find its own method (#406); `Rename.checkMeanings` around line 602 for #411.
- `hand:` the roadmap row, written by `replace_text` above row 168 (its `old`), swapped below it
  with a script: no tool moves a line.
- `hand:` each failure and each suspect refusal reproduced with `bin/jrefactor` in a scratch
  project (and the two renames on the Lang clone, under the heavy lock).

## Runs

All on Commons Lang at commit 9368465 (636 Java files, main and test, Java 8 source), JDK 21, the
jar of main 229bf11, `jrefactor.stress.counts=true` and `jrefactor.stress.refusals=true`. Every
run left the clone clean (`git status`).

| Run | Seed | Calls | Tools | Time | Applied | Refused | Failed |
|---|---|---|---|---|---|---|---|
| RefactoringStressTest | 11 | 100 | the ten new entries | 6 min | 37 | 63 | 0 |
| RefactoringStressTest | 12 | 300 | the ten new entries | 18 min | 117 | 181 | 2 |
| RefactoringStressTest | 13 | 150 | all 28 entries | 10 min | 67 | 80 | 3 |
| NarrowCheckStressTest | 13 | 100 edits | | 12 min | | | 0 misses (57.7 files re-checked per edit) |

Of every applied call: no compile error added by a full count, the narrowed count equal to the
full one, and `undo` restoring every file and the first count. The failures are exceptions out of
a tool call, each restored by the test.

New entries, seeds 11 and 12 together (400 calls):

| Entry | Applied | Refused | Failed | Note |
|---|---|---|---|---|
| `add_parameter_type_class` | 37 | 9 | 0 | refusals: gate (a body needs the old type), a Throwable, a member without `value` |
| `add_parameter_type_method` | 5 | 40 | 0 | most gated: the body passes the parameter on as the old type; the picker also offers raw `Supplier` for `Supplier<Lock>`, fairly refused |
| `add_parameter_enum_values` | 45 | 0 | 0 | Lang names about 23 enums (`enum X`) in 17 files |
| `remove_parameter_value` | 3 | 41 | 0 | almost all "the call at ... passes ...": other calls pass other arguments |
| `change_type_bound` | 9 | 33 | 2 | 2 failures: #405 |
| `change_type_wildcard` | 14 | 30 | 0 | gate refusals with the wildcard fix line; varargs refused |
| `type_switch_to_method_enum` | 0 | 0 | 0 | no target: 44 turns found none in 200 files each |
| `extract_variable_parameter_all` | 13 | 31 | 0 | 10 refusals are #406 |
| `extract_variable_parameter_call` | 6 | 38 | 0 | 7 refusals are #406; most others the effect rules |
| `extract_supertype_generic` | 22 | 22 | 0 | 8 refusals are #409 |

Commons Lang targets Java 8: of its 36 `switch` statements none is a switch expression or an
arrow switch, the forms `type_switch_to_method` takes, so the enum field and method forms (#151,
#152, #397) got no call here. They need a project on Java 14 or later.

The all-tools run (seed 13) failed twice in `rename` (#411) and once in `change_type_bound` (#405),
and found #410 among the `inline_variable` refusals.

## Bugs found (issues, reproductions in each)

- #405: the gate errors when the JDT compiler throws on the changed code (`change_type` bound,
  `replace_text` alike): seeds 12 and 13, three calls.
- #406: `extract_variable` `as=parameter` refuses every method with generic parameter types
  ("has no member apply with parameters (Function, Object)"): `Introduce` names the method by
  erased types.
- #407: `change_type` with `type_parameter` refuses `line`, which the contract says it takes.
- #408: overloads written alike (`<T> T raise(T)`, `<T extends Throwable> T raise(T)`) cannot be
  named, and the refusal suggests the name it refused: seed 11, `ExceptionUtils.throwUnchecked`.
- #409: `extract_supertype` `kind=class` writes a concrete class with no constructor, refused
  under an abstract superclass, for an abstract member, or under a superclass without a
  no-argument constructor, with a fix line that does not fit.
- #410: `inline` of a local with an array initializer writes `{...}.length` and refuses as if the
  user's file did not parse.
- #411: `rename` errors with a JDT `NullPointerException` when a re-checked file has a single
  static import of a project method (`Rename.checkMeanings` resolves import names after the
  compile): seed 13, two of six renames.

None is pinned by a golden case: `GoldenTest` has no way to keep a known-failing case out of the
build, and a case that pins today's output would pin the bug. The reproductions are in the
issues; each fix adds its golden case.

Wording, not filed: a `change_type` refusal from the gate begins "changed the type of left of ...
would add compile errors"; it should read "changing" (as the bound form does).

## Measured

Not measured: the session-log hook is not loaded here. From the notes: two batches and two
`replace_text` calls to `RefactoringStressTest` through `bin/jrefactor` (two refusals, both
answered by resending), docs by file edits.

## Assessment

- **Through the tools or around them:** the Java went through `bin/jrefactor` (one batch of nine
  steps, about 450 lines of new target pickers, then three small edits). The reproductions are
  scratch projects outside the repo, written with heredocs.
- **Large writes:** the pickers, new code with no tool to make them.
- **Did the plan hold?** Yes, plus the refusals flag. Running all the tools found more than the
  new options: #411 is in `rename`, which earlier Lang runs had passed.
- **What found the bugs:** the stress run for #405 and #411 (exceptions); reading whole refusals
  for #406, #408, #409 and #410, which the counts alone showed as ordinary refusals; and a probe
  by hand for #407.

## Follow-ups

- The issues above, each with its golden case when fixed.
- `type_switch_to_method` over an enum still has no stress run: a project on Java 14 or later is
  needed.
- The picker for `add_parameter_type_method` could offer parameterized types whole
  (`Supplier<Lock>`) as `value`, which the tool takes.
