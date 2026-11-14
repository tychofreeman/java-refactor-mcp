# Design

Status: draft, 2026-11-02. Decided items are marked **Decided**. Everything else
is open.

## Context

gorefactor (Go) gives Claude refactorings that type-check, roll back on failure
and can be batched. Its measurements show that results must be small, dependent
steps should be batched, and the server should do what the model would get
wrong. This project carries those lessons to Java.

## Decisions

**Decided: JDT Core, headless.** The engine is `org.eclipse.jdt:org.eclipse.jdt.core`
used without the Eclipse workbench. It gives a compiler, AST, bindings, search,
and `ASTRewrite`. Not chosen: OpenRewrite (batch recipes, not line-driven edits),
JavaParser (weaker type resolution), and IntelliJ PSI (hard to host outside the IDE).

**Decided: text edits applied by the server.** Each refactoring computes text
edits against the original source, applies them to disk, type-checks before and
after, and rolls back on failure. This is the gorefactor model. JavaLens returns
edits for the model to apply; that is the main reason this project does not
extend it. See the conversation notes from 2026-11-02.

**Decided: MCP over STDIO with the official Java SDK.** The dependency is
`io.modelcontextprotocol.sdk:mcp`, which is `mcp-core` plus its Jackson 3 JSON
binding; `mcp-core` alone has no JSON mapper and fails at startup. The server is
one shaded jar (`target/java-refactor-mcp.jar`) serving one project root.
Tool schemas leave out `"additionalProperties": false`: with it the SDK refuses
an unknown argument before the tool runs, without naming the arguments to use;
the tool's own check names them.

**Decided: 1-based lines** in every tool argument and result, matching gorefactor
and `inspect`-style outlines.

**Decided: Maven.** Group `io.github.tychofreeman`, artifact `java-refactor-mcp`,
JDK 21.

**Superseded the same day (the user, 2026-11-05): the read guard denies every grep of a
`.java` file again, and `inspect` has `pattern` (#202) and `names` (#203) for the searches the
relaxation below allowed; the hidden-path refusal stays. History in
`docs/how-this-project-was-built.md`.** Was: **the read guard denies a grep that shows Java text,
not one that shows none (the user, 2026-11-05).** A `grep` of a `.java` file stays denied, as in gorefactor's guard, when it prints
lines; one with `-c`, `-l`, `-L` (grep) or `-q`, which prints a count, names or nothing, is
allowed, and a path a shell variable hides is refused saying to write it out. Options it beat:
allowing `grep -n` of Java too (cheap line search, but against gorefactor's rule), and a
pattern search in `inspect` that the refusal points to (more work).

**Decided: change_type of or to a primitive refuses the uses the type decides (the user,
2026-11-05).** Probes found `int` to `long`, `int` to `double` and `char` to `int` compile
and change overflow, division and printing. A use that is an operand of arithmetic, a
shift, a unary or compound operator, an argument of a call, a cast, a branch of `?:`, a
switch selector, or `==` on a boxed type is refused (`TypeDecidedUses`); copies, returns and
ordering comparisons are allowed. Options it beat: refusing every primitive type change
(simplest, but `int` to `long` could no longer be done by the tool), and applying it while
listing the risky uses in the result (most permissive, but a normal-flow change would be
made knowingly, against the policy above).

**Decided: the behavior gate checks declared moves (roadmap #96, the user, 2026-11-05).**
Each refactoring returns, with its edits, what it does to code: a piece moved
(and what it crosses, and whether it lands where it runs only sometimes or
later), duplicated or dropped. One checker derives the effect-order
(`Effects`), same-meaning (`NameCheck`) and exceptional (`Exceptional`) checks
from that, so a tool states facts and the rules live in one place. Migrated
tool by tool; a tool's one-off rules stay only where their refusal is clearer.
Options it beat: inferring the moves from a diff of the trees (covers every tool
at once, but inlining substitutes and renames change text, so matching is
heuristic and would refuse falsely), and keeping the opt-in with a test that
every refactoring calls the three checks (cheapest, but the rules stay
scattered and `InlineFunction` does not shrink).

**Decided 2026-11-05 (the user): a release on every push to main that changes code and
passes CI.** The `release` job of `ci.yml` tags the commit with the next version after the
latest tag, bumped by the merged pull request's label (`major`, `minor`, else the patch),
builds the jar with that version in its manifest (`-Drelease.version`, which
`Server.version()` reports; any other build reports `devel`), and creates the GitHub
release with generated notes and the jar attached. Not chosen: a version raised by hand in
a PR (gorefactor's first scheme, which let main run 53 PRs past its release), a date or a
patch-only bump, and committing the bump back to `main`. The first release is v0.1.0.

**Decided: separate repos** for the Go and Java servers. A shared repo for the
tool contract and the A/B harness is planned once a second language exists.

**Decided 2026-11-03: stubs travel in the call's result, not a side channel.**
An edit's declared stubs are returned in the result object of its call. Today
`Stubs` collects them in a `ThreadLocal` and `Tools.call` appends them to the
text, so a handler's output depends on state it does not return. The user chose
the result object. Not chosen: keep the side channel (the option it beats),
which needs no change to handlers but hides their output from tests and from
any caller that is not `Tools.call`.

**Decided 2026-11-04: effects in normal flow are preserved; exceptional behavior is not, by default.**

This extends the definition of refactoring as a behavior-preserving transformation. A
transformation cannot preserve every kind of behavior, so a refactoring is defined by the kinds it
keeps, and choosing those kinds is part of the design. Normal logic flow is the kind kept by
default. Exceptional behavior is kept only where a guard or a try/catch makes it part of the normal
logic; elsewhere it is a concession, made knowingly and reported.
A refactoring keeps every effect that is part of the normal logic flow: calls, assignments and
reads, and their order. Exceptional behavior (an exception thrown, a null dereference, an array
index failing, anything outside the normal flow) is not preserved by default, with two exceptions
that are kept without a flag:
- a guard: a null check or condition that stops the exception before it happens (`if (x != null)
  x.f()`), since the guard is part of the normal logic;
- a try/catch: the source of an exception that is caught stays inside the `try` block.

The model can require exceptional behavior to be preserved with a flag, for a tool that can
change it. A tool that detects a change in exceptional behavior it did not preserve says so to the
model in its result, so the model can decide whether the change is acceptable. Not chosen: always
preserving exceptional behavior (it would refuse most moves that pass the normal-flow rule, such
as an inlined initializer that may throw) and ignoring it (the model would not know).

**Decided 2026-11-13 (the user): a guard keeps a division or a dereference only when it tests
the operand (roadmap #173).** This narrows the 2026-11-04 decision for two unchecked exceptions.
Code that may throw only ArithmeticException (an integer `/` or `%`) or NullPointerException (a
dereference of a variable or expression) keeps a guard only when the guard tests its own operand:
compares the divisor to a constant number (`d == 0`, `d > 0`) or tests the receiver for null
(`x != null`, `Objects.isNull(x)`, `Objects.nonNull(x)`, `x instanceof T`), the same variable,
field or expression by binding (`Guards`). Moving it from under such a guard is refused as
before (`if (d == 0) return 0; return n / d;`); from under any other guard it is reported through
`Exceptional`, as a move outside guards already is (`if (items.isEmpty()) return 0; return total
/ count;`), and so refused under `preserve_exceptions`. A try/catch keeps it as before, and
everything else that may throw (an array index, a reference cast, a call, `new`) keeps the 2026-11-04
rule. The rule is the shared gate's (`Behavior`), so every refactoring that declares a `Move` has
it. Options it beat: (a) only a try/catch that handles the exception counts, any guard ignored;
(c) keep the 2026-11-04 rule. Defaults taken in building it, to reopen: a divisor's test is a
comparison with any constant number (`d >= 1` too); a switch on the operand counts as testing it;
leaving a catch clause, a lambda or an anonymous class is still refused (they run the code at
another time rather than guard it); and the same rule applies when a guard moves below code that
may throw, since `move_statement` makes that swap either way.

**Decided 2026-11-05: an edit chooses an ambiguous import on evidence (the user, roadmap #105).**
When a simple name could be several classes, the import fix takes the one the file imported
before the call or its batch, else the only one in a package the file imports from, and names the
choice in the result ("chose java.util.Date over java.sql.Date: the file imports from java.util").
Without such evidence it refuses once, listing every ambiguous name, with a `java.*` candidate
suggested first. Options it beat: choosing only a prior import (fewer guesses, but the
`org.eclipse.jdt.core.dom` family case still refused), and never choosing (one refusal for all
names, but still a resend). The type check stays behind every choice.

**Decided 2026-11-07 (the user): the model should not be concerned with layout.** `add_declaration`
places each new member by kind: a field or initializer after the type's last field, a constructor
after its last constructor or field (before its first member when it has none of those), and any
other member at its end. `after` and `before` left the published schema and are accepted and
ignored, so no description offers the model a layout choice; a member that replaces a stub keeps
the stub's place. Options it beat: the same placement with `after`/`before` still published (a
smaller change, but the description keeps offering the choice, and a run sent `before` it did not
need), and leaving members at the end. Order does not change what a class does (only field
initializers and initializer blocks run in the order written, and a field still goes after the
fields), so placement is a convention for readers, and the tool can keep it alone. The tools that
add a member elsewhere follow the same rule (2026-11-07, `MemberMove.addTo`): `pull_up`,
`push_down`, `move` of a static field and `extract_variable` with `as` put a field after the
target's fields; a method still goes at the end.

**Decided 2026-11-07 (the user): an edit that leaves final fields unset writes their
constructor.** When an edit adds a final instance field with no value to a class that has no
constructor but "not implemented" stubs, the edit adds one taking all such fields in
declaration order, after the last of them (a stub of the same parameter types is replaced in
its place), and the result names it; a top-level class that then fits `convert_to_record` is
named so. A class with a real constructor is still refused, naming `constructor`: which
constructor sets the field is the model's choice. Options it beat: a stub constructor from a
test's call that assigns fields matching its arguments by type (chosen as well, built next the
same day: a bigger guess, and real behavior where a stub fails; it assigns only when each
argument fits one field), and only a refusal that suggests a
record (no turn saved). Before, the refusal named `constructor` (#265), and A/B runs showed a
turn spent on each such steer.

**Decided 2026-11-13 (the user): generators as a `build` argument, `replace_parameter` first as
`remove_parameter` with `value`.** The user reviewed `docs/sessions/generators-sketch.md` and
chose the recommended option of each of its open questions. (1) The generators are an argument,
`build`, on `insert_statements`, `replace_statements` and `replace_expression`, with gorefactor's
names as unpublished aliases; over one `build_statements` tool with a `kind`, and over
`create_loop`, `create_call`, `create_guard` and `replace_parameter` as published tools. (2) A
model is steered to them by a refusal under `JREFACTOR_SKELETON_ONLY`; over the description alone,
and over an A/B arm publishing the aliases. (3) `replace_parameter` is first the query form,
`remove_parameter` with `value` an expression of the other parameters; over gorefactor's form (a
new parameter computed from the old one, by closing `extract_variable` `as=parameter`'s two gaps),
and over one tool routing to both. (4) Generated declarations write explicit types; over `var`
where the file uses it, and over an argument. (5) The loop generator writes the shell with
`body`; over gorefactor's `op`, and over `op` written as a stream. (6) A call's arguments are
always given in `args`; over filling them from same-named variables in scope, and over arguments
by parameter name. (7) The order is `replace_parameter`'s query form, then call, loop, guard;
over the loop first, and over leaving the guard out.

Built with the query form (roadmap #147), three defaults the sketch left open, each the smallest
that keeps behavior, to reopen if they cost turns. An expression is always declared as a local at
the start of the body (`int area = width * height;`), as the sketch's examples show, also for one
read; over writing it in place of a single read, which the increment's case names suggested but
which needs the read's type and time to be the parameter's. A constructor that passes the
parameter to `this(...)` or `super(...)` is refused; over writing the value inline there. A call
that passes a primitive of another type for a parameter the value reads (`int` for `long`) is
refused, since the call computed the value in the argument's type and the method would compute it
in the parameter's; the sketch's six checks did not name it, and a probe
(`remove_parameter_value_argument_type`) shows the overflow.

With a call in the value (roadmap #148), two rules are stricter than the sketch wrote them, both
to keep behavior. The sketch asked that every argument after the value be effect-free; it must be
fixed (locals, parameters, constants, literals), since an argument that only reads a field would
be read before the value's call where the call read it after (probe
`remove_parameter_value_call_later_read`). And an argument the value reads that is not fixed must
be read in the value before any of its calls, since the call read it a second time there (probe
`remove_parameter_value_call_argument_reread`). A constructor is refused whatever it starts with:
the field initializers and the constructor above would run before the value's call.

The other direction, `extract_variable` `as=parameter` with `all_occurrences` and with a call
(roadmap #161), mirrors those rules and takes three defaults the sketch left open, each the
smallest, to reopen if they cost turns. `all_occurrences` is built only with `as=parameter`,
and refused with a local, a constant or a field; over building it for all four. A call in the
expression must have a receiver that is a parameter, a chain from one, or a class: a call on
the method's own object (`rate(order)`, `this.rate(order)`) or a static method named without
its class is refused (reversed by #169, below); over writing it at each call on that call's receiver (the matcher's rule
in the other direction) and over qualifying a static method with its class at a call in
another type. Only an expression with a call is reported through `Exceptional` as now throwing
at the call; a may-throw expression without one is checked by the behavior gate (refused under
a guard) but not reported, as before; over reporting it too, which would add a report to every
`qty / 10`. The extraction is now one change (one undo), `Signatures.addParameter` taking the
occurrences' edits from its caller (`more`), where it had been two writes.

**Decided 2026-11-13 (the owner's brief, smallest option): overloads written alike are named by
erasure** (roadmap #177, issue #408). `<T> T raise(T t)` and `<T extends Throwable> T raise(T t)`
are both written `raise(T)`, so no name told them apart, and the refusal suggested `raise(T)`.
Now `name` also takes the erased parameter types (`raise(Object)`, `raise(Throwable)`), tried
after the written ones, and the refusal and `line` give that form for a method written like
another of its type (`Declarations.methodLabel`). Over naming with the bound (`raise(T extends
Throwable)`, a new syntax to parse and match, and long for intersections) and over pointing the
refusal at `line` alone (which works only once the name `line` turns into is itself unambiguous,
so it needed this anyway). Erasure is already how stubs compare signatures (`Declarations.stubFor`,
roadmap #107) and how the compiler tells the two apart; two methods of a type cannot erase alike.
The erasure is read from the syntax, as `find` is syntax-only: a type variable of the method or a
type around it, by name. `MemberMove.member` (`pull_up`, `push_down`, `move` of a member) keeps
its own field-or-method rules, and since #483 reads the name and matches overloads, erased ones
too, through the same `Declarations.memberName` and `Declarations.overloads`.

**Decided 2026-11-13 (the owner's brief, smallest option; a default to reopen): `convert_to_record`
refuses a constructor less visible than the class and a transient field, and carries field
annotations onto the components** (issue #476). Over writing the record and naming the widened
constructor in the result, and over keeping a compact canonical constructor (JLS 8.10.4 requires it
to be at least as visible as the record, so it cannot stay private). Over dropping `transient` and
saying so. Over refusing a field with annotations: where an annotation's targets allow, the record
also puts it on the accessor and the canonical constructor's parameter, which the result does not
name yet. An anonymous subclass (`new Point(1, 2) {}`) is still left to the gate's compile error,
not refused by name.

**Decided 2026-11-13 (the owner's brief, smallest option; a default to reopen): in a named module
the gate does not count a sealed type's permitted class in another package** (roadmap #180,
issue #438). JDT's headless parser checks every file as the unnamed module: `ASTParser.setEnvironment`
takes a classpath and a source path, no module path, and `CompilationUnitResolver` builds each
unit with the batch `CompilationUnit(char[], String, String)`, whose module name is null (checked
with `javap` on JDT 3.47.0). So `permits b.Circle` from package `a`, legal in a named module, is
error 1859 (`IProblem.SealedPermittedTypeOutsideOfPackage`) before any edit, and a `rename` or
`move` of the sealed type's file carried it to a new key and was refused. Now `TypeCheck.counts`
drops that one problem when a source root holds `module-info.java` (`SourceProject.namedModule`);
a single-module project is one module, so the rule it stands for (same module) always holds.
Over (b) checking as the named module through JDT internals (the resolver and `FileSystem` with
module names, which the public API does not reach and a JDT bump could break), and over (c) a gate
rule that an error the project had before, carried by a rename or move to a new file or name, is
not new (general, but it would also pass a real error so carried, and it needs the gate to know
what a tool moved). Left as they are: `module-info.java`'s own errors (each exported package reads
as missing, pre-existing so never new) and `create_file` of a `module-info.java`, refused as not
parsing (the syntax parse has no unit name).

**Decided 2026-11-13 (the user): the new parameter goes right after the last one the
expression reads** (roadmap #172). `extract_variable` `as=parameter` refused a call with an
argument that has effects after one the expression reads (`run(step, tick())` for `q * 3 + 1`:
the new last argument would read `step` after `tick()`). The new parameter now goes right after
the last parameter the expression reads, in every declaration, lambda and call of the family,
so the new argument runs after exactly the arguments before it: `run(step, step * 3 + 1,
tick())`. Over keeping the refusal and over declaring a local at each call. When the expression
reads no parameter, or its last is already last, it stays last. Refused still: an argument with
effects between a read argument and the new place (`run(step, tick(), 5)` for `q + r`); an
argument with effects before the read one is no change (`run(tick(), step, tick())` for `r * 2`
applies), since the parameter and the new argument both read it after that argument. One
default, the smallest, to reopen if it costs turns: an expression that calls or may throw stays
last, under its existing rules, since placing it earlier would run its call, or its exception,
before the later arguments' effects. `remove_parameter` `value` has no mirror: the argument
goes away and the value is computed in the body after every argument, so there is no place to
choose; #147's rule for the arguments after it stands.

**Decided 2026-11-13 (the user): an argument of another type is cast, not refused** (roadmap
#171). Where `extract_variable` `as=parameter` refused a call passing a primitive of another
type than the parameter the expression reads (#161), it writes the argument cast to the
parameter's type, `(long) n * 1000000` for `x * 1000000`, so the expression runs in the type the
body used; the call had made the same conversion (widening, boxing or unboxing), and a cast
context allows each of them. Over refusing. The cast goes only where the types differ, without
parentheses except around a receiver (`((Long) v).hashCode()`). Two limits, defaults to reopen:
a cast is written only to a primitive or a java.lang type without type arguments (another
reference type's name may not be imported in the calling file, and a type variable cannot be
cast to from a primitive), else refused as before; and `remove_parameter` with an expression
`value` keeps its refusal (probe `remove_parameter_value_argument_type`), since no cast can
reproduce it: there the call computed the argument (`x * y` in `int`) and the body would
compute the value in the parameters' type, and a cast of the body's parameters would be wrong for
calls that pass the parameter's own type.

**Decided 2026-11-13 (the user): a call on the method's own object is written on each call's
object** (roadmap #169). `extract_variable` `as=parameter` of an expression with `rate(order)`
or `this.rate(order)` in it writes the call on the object each call of the method runs on: at
`x.m(a)` the new argument is `x.rate(a')`; at `m(a)` or `this.m(a)` in the class or a subclass it
is `rate(a')`; at `super.m(a)` it is `super.rate(a')` only when that means the same method (no
override of `rate` in the calling class or below it), else refused. Over qualifying the call
(which has no receiver to qualify it with at a call in another type) and over refusing, as
#161 did. The static counterpart is the same decision: a static method named without its class
(`scale(x)`) is written `Pricing.scale(x')` at a call where its name alone would not reach it
(outside the class, its subclasses and the types nested in them) and left bare elsewhere.
What keeps behavior (`ParameterValue.sameTargets`): the call's receiver is now evaluated twice,
so it must be steady (this, a class, a local or parameter no argument assigns, or a final field
of one), else "put it in a local first"; an unqualified call of the method from a type that is
not the class or a subclass (an enclosing object's) is refused, naming `Outer.this.m(...)`; each
call in the written argument must resolve, in a probe parse of the calling file with the
argument in place, to the method the body called or an override of it (another overload, or a
method of a nearer type, is refused), and one the call site cannot see is refused naming the
fix, "widen it first". Two defaults, the smallest, to reopen if they cost turns: a call on a
qualified `Outer.this` in the body stays refused, and at `super.m(...)` the tool does not write
`rate(a')` in place of `super.rate(a')` (which would also keep behavior, since a method with
one declaration runs the same body either way), as the decision names `super.rate`.

**Decided (the user, 2026-11-13, reopening the default below): a `batch` step's arguments beside
`tool` are read as its `args`, and the result names the steps (options (b) and (c));
`Batch.underArgs`, roadmap #198. A key in both places with different values is refused. What was
first decided:** a `batch` step with its arguments beside `tool` is
refused, not read. A step `{"tool": "replace_text", "file": ..., "old": ...}` was refused by the
step's tool ("replace_text needs file"), which does not say where the arguments go; four friction
entries (2026-11-10, three on 2026-11-13). Options: (a) refuse before any step runs, naming the
keys and showing the step with them under `args` (chosen: the smallest, and the model learns the
shape once); (b) read such a step's keys as its arguments when it has no `args` (no turn lost, but
two shapes for one thing, and a key that is both a step key and an argument would be ambiguous);
(c) read them and say so in the result (as (b), with the note costing tokens on every step). The
owner asked for the smallest choice; reopen (b) if the refusal still costs a turn each session.
`Batch.argumentsUnderArgs` (roadmap #196).

**Default to reopen (2026-11-13, issue #469; the owner said take the smallest fix):** the name
checks of `extract_function` and `extract_variable` walk every type around the extraction and
each one's supertypes, refusing a name any of them declares (a method, a field). Options: (a) that
walk (chosen: small, in each tool's own check, and it errs toward refusing a name no call there
uses); (b) a check on meaning over the whole enclosing type, as `NameCheck` does for moved code:
every simple name outside the edit must keep its binding (exact, so no false refusals, but a second
parse of the file and a new entry point in `NameCheck`); (c) only the shapes the issue names. Still
not checked: a local of an enclosing method that the new local, in an anonymous or local class's
method, would hide, and a static import of the name. Reopen (b) if the refusals cost turns.

**Default to reopen (2026-11-13, issue #474; the owner said take the smallest fix):** a `throw`
that no try inside the statement catches makes `Effects.leaves` true, so a guard that throws is an
early exit, as one that returns: an effect moved past `if (n == 0) throw err;` is refused, and code
that may throw moved above it is refused when the guard tests its operand, reported otherwise.
Options: (a) that (chosen: one rule for every exit, and `throw new X()` was already refused, as
the new object counts as a change); (b) report the effect's move as a change to exceptional
behavior, refused only under `preserve_exceptions` (the effect would then run on the throwing path,
which the policy keeps for normal flow); (c) refuse only a throw of a variable. A catch is matched
by the thrown expression's static type against each catch clause's type (a union's alternatives
too); a throw a catch takes only by its runtime type counts as leaving. `Effects` also marks an
`assert`, an integer `/=` or `%=` and an unboxing as may-throw; no golden case or probe changed.

**Default to reopen (2026-11-13, issue #477; the owner said take the smallest fix):** `inline`
of a method keeps the lines inside a text block as they are when it re-indents the body
(`InlineFunction.reindent`, given each text block's source from `InlineFunction.textBlocks`, found
again in the substituted text in order). Options: (a) that, in `InlineFunction` (chosen: the
smallest, and the way `Extraction.reindent` already does it); (b) the shared `shift(text, from, to,
protected ranges)` in `Lines` that the review suggests, with `Extraction` moved onto it (the
Indent issue, a later item); (c) refuse a body with a text block.
**Default to reopen (2026-11-13, issue #479; the owner said take the smallest fix):** a file the
journal deletes takes with it the directories it empties, stopping at a source root or the
project's root (`Journal.removable`; `put` and `restore` take the project). Options: (a) that
(chosen: no state, so it holds across the command line's runs too); (b) also recording the
directories each call creates, so a directory the user made empty elsewhere stays (the review's
second half; needs the journal's entries, saved by the command line, to carry them); (c) never
removing a directory.
**Default to reopen (2026-11-13, issue #490; the owner said take the smallest fix):**
`Server.answerAll` reports every `Error` of a call as an engine error and keeps serving, except a
`VirtualMachineError` other than `StackOverflowError` and `OutOfMemoryError`, which it rethrows.
Options: (a) that (chosen: an overflow or a full heap ends with its call, whose frames are gone);
(b) only `StackOverflowError`, as the review's minimum; (c) every `Error`, `InternalError`
included.
**Default to reopen (2026-11-14, issue #530; smallest option):** `change_signature`'s list
arguments (`add_throws`, `remove_throws`, `stop_at`) stay comma-separated strings, read by
`Args.commaSeparated` (moved from `ChangeSignature.names`). Options: (a) that (chosen: no change
to the contract, to the description's `add_throws=IOException`, or to the command line's
`key=value` form); (b) arrays (`Arg.strings`, read by `Args.list`) as the other tools' lists, which
refuses a call that sends one string unless `Args.list` also takes one; (c) both, an array or a
comma-separated string.
**Decided (2026-11-14, the user, issue #364, roadmap #206): `change_try` `remove_resource` moves
the resource out and closes it**, over moving it out without closing it and over refusing when the
body uses it. The declaration goes just before a try of the body (with the resources after it)
whose finally closes it, under `if (r != null)` unless the initializer is a `new`; a Java 9
resource (`try (r)`) has no declaration to move. As JLS 14.20.3 translates a try-with-resources,
the resources before it, the catches and the finally stay on a try around the declaration and the
new try, so the resource still opens after the earlier ones and closes before them and before any
catch, a catch still sees its initializer's and its close()'s exceptions, and the finally still
runs; that try is dropped when it keeps none of them, which is the user's form, the declaration
just before the try. One change is left, said in the result: an exception close() throws while
another is thrown replaces that one, where the try-with-resources added it to the other's
suppressed exceptions (probe `change_try_remove_resource_suppressed`).
**Default to reopen (2026-11-14, roadmap #206):** a try that keeps catches, a finally or earlier
resources gets the close in the new inner try's finally, not first in its own finally as the
user's description of the choice said. Options: (a) that (chosen: as faithful as the form allows,
probes `change_try_remove_resource_first`, `_last`); (b) the declaration before the whole try and
the close first in its finally: the catches would run before the close, the close's and the
initializer's exceptions would escape them (the usual `catch (IOException e)` around a reader is
then refused by the gate, close() unhandled), the finally would not run when the initializer
throws and would stop at a close() that throws; (c) refusing such a try.
**Decided (2026-11-14, the user, roadmap #206): `change_try` `add_resource` is text with a
`position`**, over declaration text only and over text or a local moved in: a declaration
(`Reader r = open()`) or an effectively final variable (Java 9), at a 1-based place in the list,
last by default (resources close in reverse order); a plain try becomes a try-with-resources.

**Decided (2026-11-14, the user): the roadmap is one file per item in `docs/roadmap/`**, named by
its item number, with `order`, `item` and `status` in a header; `bin/roadmap` prints the table
and `RoadmapTest` refuses a number two files share. Options it beat: fewer agents at once (one
or two, so the table conflicts less: slower, and it still conflicts), and keeping the table with
a stricter procedure for resolving its conflicts (that procedure had already deleted one row and
brought back another, and numbers were still taken twice). Why: every PR appended a row at the
end of one table, so each merge to `main` put the other open PRs in conflict.

**Decided (2026-11-14, the user, the follow-ups of #501 in `docs/sessions/one-label.md`): one
parser for `Type.member(params)`**, `Qualified.parse`, over leaving the four splitters until a
refusal showed a model sending one tool's label to another. The name splits at its last dot before
the parameter list, so the type may be nested (`Outer.Inner.m`) and the parameter types may hold
dots, commas inside angle brackets and varargs (`m(Map.Entry, Map<K, List<V>>, int...)`).
`Qualified.of` (which split at the first dot), `Fold` (the last two segments), `Implement` (the
last dot, compared with the simple name of the declaring class), `TestFor` (type or method guessed
by the case of the last segment) and `Declarations.memberName` read names through it.
**Default to reopen (2026-11-14):** the tools that take a top-level class (`Opened.topLevel`:
`pull_up`, `push_down`, `move`, `encapsulate_field`, `change_type`, `inline` of a method) now
refuse `Outer.Inner.m` saying it is a member type, over making each of them act on member types
(their call and import rewriting assume a top-level class; each would need its own goldens).
`TestFor` tries the whole name as a type first, then the last segment as a method of the rest.

**Decided (2026-11-14, the user, the follow-ups of #501): a label that names a method is written
as `Declarations.methodLabel` writes it**, the parameter types as source writes them, erased only
when two overloads of the type would otherwise read alike (option (a)), over (b) every label
erased, as `Names.methodLabel` was (one rule, but `List<String>` reads as `List` and `implement`
would have to take erased names), and over (c) keeping both forms, documented (a label one tool
prints might not be the one another prints). `MoveType.label` (the owner after a move) and
`Rename.describe` (prose) keep their own forms.

## Constructive transformations (2026-11-05)

The thesis widened (the user, 2026-11-05): not only that refactoring lets a model think at a
higher level, but that transformations as a whole do. In a good IDE a programmer types only
sometimes, and then thinks about structures, not letters: the class, its constructor, the stubs an
interface asks for, a test method's shell and the `try` around a call come from commands; what is
typed is the idea, an expression or a few statements. Here `create_file` was the one place with no
such commands, so a model typed whole classes through it: about 5,600 lines of `src/main` and
2,000 of `src/test` since the session log began, the largest a 592-line tool in one call.

The vocabulary, by level, and what it has:

| Level | IDE move | Here | Missing |
|---|---|---|---|
| File and type | new class, interface or record in a package, with supertypes | `create_file` (whole text) | a type by its declaration, in the right source root |
| Members from structure | implement or override methods, generate a constructor, accessors, `equals`/`hashCode`, delegates | `encapsulate_field`; stubs from usage | implement/override; constructor from fields |
| Members from usage | create a method from a call | stubs, automatically | |
| Tests | create a test for a class or method | nothing | a test class, and a test method that calls the target |
| Statements | surround with if, for or try; introduce a variable; add a case or element | `insert_statements`, `replace_statements`, `extract_variable`, `add_case`, `add_element` | surround with |
| Expressions | typed | `replace_expression` | (typing belongs here) |

What is measured changes with it: beside the share of edits made through the tools, the share of
typed text that is statements and expressions rather than structure. The A/B harness can test the
thesis on feature tasks (gorefactor's `ab-features`, ported), an arm with skeleton-only creation and
the constructive tools against the current tools.

**The first slice (the user, 2026-11-05), one PR each, in this order:**

1. `create_file` with `declaration` in place of `content` (roadmap #114): the package line and the
   declaration (`class Cart implements Priced`, `record Line(int qty)`), with an empty body; with
   supertypes, a stub for each abstract method they leave unimplemented. The file's path follows
   from the package and the type's name, in the main source root unless `test` is set.
2. A test from its target (roadmap #115): `create_file` with `test_for` (`Cart`) writes the empty
   test class in the test root's mirrored package; `add_declaration` with `test_for`
   (`Cart.total`) adds a test method that builds the receiver and calls the method, ending in
   `fail(...)`, so it is red until the model types the assertion.
3. Skeleton-only creation, an experiment behind a flag (roadmap #116): a Java `create_file` with
   member declarations in its content is refused, naming the operations above. Off by default, as
   the write guard started; the A/B arm turns it on.

Choices in the slice and the options they beat (recorded as defaults; reopen with evidence):

- **Options on existing tools, not new ones** (CLAUDE.md's rule): `declaration` and `test_for` on
  `create_file`, `test_for` on `add_declaration`. Beat: a `create_type` and a `create_test` tool,
  clearer names but two more tools to describe, against the evidence that fewer, wider tools served
  gorefactor's models.
- **A generated test is red** (`fail("write the assertion")`): test-first, as CLAUDE.md works.
  Beat: an empty body that passes (a green test that tests nothing), and a guessed assertion on the
  result (wrong more often than not).
- **The test framework from the classpath:** JUnit Jupiter when it resolves, else JUnit 4, else
  refused. Beat: a framework argument (one more thing for the model to state).
- **The flag covers `create_file` only:** `add_declaration` keeps whole members, one per call, so
  the model types a member at a time and the stub, implement and test operations give the shells.
  Beat: refusing bodies in `add_declaration` too (a stricter test of the thesis, but it needs the
  member-level generators first).

Next after the slice, by what its use logs: implement/override as its own operation on an
existing type, a constructor from fields, and surround with; built 2026-11-06 as
`add_declaration` `implement` and `constructor` and `replace_statements` `surround` (#123).

**The first measurement (2026-11-05, `docs/sessions/ab-shop-tasks.md`):** on the four shop
feature tasks, Sonnet, 2 runs each, every arm passed every task; per run manual $0.107,
mcp $0.170, mcp with skeleton-only $0.217. The skeleton rule redirected typing (each
refused file became `declaration` plus one `add_declaration` of the same members: 811
typed lines against 575) and `test_for` went unused, so the slice alone does not replace
typing; the member-level operations above are what would. One run built its new type by
transformation (`extract_struct`, then `move_type`) and was stopped by a gap on records
(roadmap #118).

**The second measurement (2026-11-06, `docs/sessions/ab-constructive-ops.md`):** the skeleton
arm again, after #123 and the day's fixes: 8/8, $0.186 a run (from $0.217), 564 typed lines (from
791), errors 18 (from 34), about the cost of the arm without the flag. `implement`,
`constructor`, `surround` and `test_for` went unused: the gain came from refusals, imports and
`batch` `retry`, which a model found from its refusal text and used. These tasks give the
constructive options little to do; a task set that does is the next measurement.

**The third measurement (2026-11-06, `docs/sessions/ab-library.md`):** a task set built for the
constructive operations (a new implementation, a class from fields, a try around a call, tests).
Manual 8/8 at $0.082 a run; mcp 6/8 at $0.097; skeleton-only 7/8 at $0.117. No run used
`implement`, `constructor` or `surround`; the three failures wrote Java by shell, were refused by
the guard and never called a tool. Options inside descriptions are not found; a refusal that
names them (as skeleton-only names `declaration`) is the mechanism left to try.

## Generators: the `build` argument (2026-11-13)

A statement or an expression built from names, the step after the member-level constructive
operations above: the model gives a method, its arguments and a name, and the tool writes the
statement with the types bindings give. The user reviewed a sketch with examples and chose the
recommended option of each question (2026-11-13):

- **Where they live:** one argument, `build`, on the existing edit tools, in place of `content`
  on `insert_statements` and `replace_statements` and of `new` on `replace_expression`; a list
  of generator objects and typed strings that nests (`body`), with gorefactor's names
  (`create_loop`, `create_call`, `create_guard`, `replace_parameter`) as unpublished aliases.
  Over one new `build_statements` tool with a `kind` (one name to find, but a union of
  arguments and no nesting), and over a published tool per generator (names that are the
  intent, but four tools, a step per statement and about twice the characters of typing the
  Java).
- **How a model finds them:** under `JREFACTOR_SKELETON_ONLY`, typed `content` that is one
  statement a generator writes is refused, naming the `build` to send. Over the description
  alone (three A/B sets found options in descriptions unused) and over an A/B arm that
  publishes the aliases.
- **Types are always written out,** never `var`, as `extract_variable` and `convert_loop`
  write them. Over `var` where the file uses it, and over an argument.
- **The loop is a shell with `body`,** no `op` (`sum`, `filter`, ...): `loop_to_stream` turns
  the shell into those. Over gorefactor's `op` writing the accumulator, and over `op` writing
  a stream.
- **A call's arguments are always given in `args`:** nothing guessed. Over filling each
  parameter from the one variable in scope of its name and type, and over `args` by parameter
  name.
- **Order of the slices:** call and construction, then the loop, then the guard (with
  `replace_parameter`'s query form, `remove_parameter` `value`, built beside them).

How it is built (`Build`): the entries are written into a copy of the file, first with every
`into` as an assignment, to see by binding which names are variables there already, then with
`var` for each new local; that copy, with its imports fixed, is parsed and each type is read
from the `var`'s binding and written with `Extract.typeName`. Checked before building on it
(2026-11-13): JDT resolves `var` for a local and in an enhanced `for` as javac does; a diamond
with nothing to infer from gives `ArrayList<Object>`, so that case is refused, naming `type`; a
call that does not resolve gives a `var` of type `Object` with no recovered flag, so a failed
call is told by its own type binding being null, not by the local's.

Defaults taken where the sketch was silent, to reopen if they chafe: a constructor's wrong
argument count is refused only against explicit constructors (a class with none keeps the
stub that assigns fields); `type` beside an `into` that is already a variable is refused, not
ignored; a checked exception and a mismatched `into` are left to the gate's wording. A loop variable's name that the tool picks is kept free of the names its body
declares too (`order2`), found with `surround`'s loop (#163); a `name` the model gives that a
body declares is left to the gate's compile refusal. For the guard (#164): a `return` with a `value` where the method returns
nothing is refused before the gate; `guard_null` puts what it names in parentheses unless it is
a name, a call or an access; a guard adds nothing to the result's `built` list, since it declares
nothing. Under skeleton-only (#167), what is refused is one statement whose
types `build` reads from the code: a declared local from a call or construction, a for-each, an
index loop. Assignments, plain calls, returns and guards pass (they type no type), as does
a declaration with modifiers or `var`; the suggested `build`
puts a construction's declared type in `type` when it differs from the class, never a call's.
Content of several statements first passed too; reopened 2026-11-13 by the user, below.

**Decided 2026-11-13 (the user): skeleton-only checks each statement of the content.** The
generator A/B (`docs/sessions/ab-generators.md`) showed the rule never fired on the invoice task,
whose typed locals came in a block with a `return`. Content of several statements is refused
when one of them is a statement `build` writes from names; the suggested `build` holds an entry
for each such statement and the others as typed strings, so it is sent in one call. Chosen over
suggesting a `build` only when every statement is an entry (which would not fit that block), and
over leaving the rule as it was (the A/B then says nothing about the refusal on call chains).

## Questions (all answered)

No question is open. These were open when the design was written; each says how it
was answered.

0. **How exceptional behavior is preserved and reported.** Answered 2026-11-04 by the user: one
   `preserve_exceptions` flag on every refactoring (option a's first form), and the alert as a
   separate field in the result (option b's second). Detection starts with the syntactically clear
   cases below; roadmap #73, after #71 and #72. The options as first set out: Options: (a) one flag name on every refactoring (`preserve_exceptions=true`), or one
   per kind of exceptional behavior (`preserve_null_checks`, ...); (b) the alert as text in the
   result, or as a separate field the model must read. Detection is the hard part: a tool can
   name what it moved past (a dereference, a call that may throw) without knowing whether it
   throws. The first step would be a list of the exceptional behaviors each refactoring can
   change, from the probes, and one tool (`move_member` already has a known null-receiver change)
   as the test.

1. ~~**Running JDT Core without OSGi.**~~ Answered by the spike in
   `spikes/jdt-headless/` (2026-11-02). Headless `ASTParser` with
   `setResolveBindings(true)`, `setBindingsRecovery(true)` and
   `setEnvironment(classpath, sourcepath, encodings, true)` works with no
   workspace and no OSGi runtime started:
   - Types from other files on the source path resolve, as do JDK types.
   - Library jars resolve only when on the classpath. Without them, the imports
     and their uses are reported as problems and the calls come back unresolved.
   - The unit still parses and the rest of its bindings resolve; missing types
     are recovered, not fatal.
   - No Eclipse platform warnings were printed.
   - Caveat: `jdt.core` 3.47.0 pulls about 30 transitive jars (`org.eclipse.core.*`,
     OSGi, equinox, jna). They are needed on the classpath, but the spike did not
     test whether the whole set is required. Trim later if it matters.
   Still open: the after-edit type check (re-parse the edited sources and compare
   problems), timing on large projects, and deriving the classpath from Maven and
   Gradle.
2. ~~**Project loading.**~~ Decided 2026-11-02 (roadmap #7): a `pom.xml` at the
   root means source roots `src/main/java` and `src/test/java`, and the test
   classpath from Maven itself (`maven-dependency-plugin:3.11.0:build-classpath`,
   pinned, run by the project's `./mvnw` or `mvn`, its output kept off stdout).
   Not chosen: parsing `pom.xml` by hand (no transitive dependencies) and
   embedding Maven Resolver (a large dependency for one call). No build file, or
   Maven failing, falls back to the root as the one source root with no
   classpath, and says so on stderr. Still open: Gradle, multi-module builds,
   non-standard source directories, and reloading when `pom.xml` changes.
3. ~~**Undo.**~~ Decided 2026-11-02 (roadmap #8), as gorefactor: in memory per
   server process (`Journal`), one entry per top-level call or batch. Not chosen:
   backups under the workspace (they would be written into the user's tree).
4. ~~**Batching.**~~ Decided 2026-11-02 (roadmap #8), as gorefactor. Lines are
   followed by a line diff of each step's file (common first and last lines), not
   by JDT positions: every tool's writes are whole-file texts.
5. ~~**Versions.**~~ Resolved 2026-11-02: JDT Core 3.47.0, MCP SDK 2.1.0 and JUnit
   5.14.4 resolve from Maven Central and `mvn verify` passes on the scaffold.

## Roadmap

Each item is a file of its own in `docs/roadmap/`, named `<item>-<slug>.md` (since 2026-11-14;
it was a table here, and parallel PRs that each added a row at its end conflicted on every merge).
A file starts with a header between `---` lines, `order` (the place among the items, `none` for
the first ones done), `item` (the number records and notes cite as "roadmap #N"; stable) and
`status` (`Open`, `Partly done`, `Done` or `Closed`), then the row's text under `## Item`,
`## Status` (how it was finished, with the date and the record) and `## Evidence`.
`bin/roadmap` prints them as one table sorted by order, `bin/roadmap --open` the open items only,
and `bin/roadmap next` the next free order and item numbers. To add an item, fetch `main` and add
a file with those numbers; when an item is done, set `status: Done` and write its `## Status`.
`RoadmapTest` fails the build when two files share a number or a header is malformed.

### Open, in order

Ordered by gorefactor's evidence (see "Why this order" below) and this repo's
friction log; reorder when this repo's own log says otherwise.

**Next, in order (2026-11-06):** this repo's open rows merged with gorefactor's
`docs/parity.md` (its section 5 order for Java, this repo's friction log and A/B runs between):

1. The parity bugs: #119 (`replace_text` inside indentation), #107 (stubs), #108 (a batch's
   imports). #106, the fourth, is done.
2. The contract: #120 (shared names, aliases); #111 (`inspect` lists the project).
3. Refusals and roots from this repo's log: #110 (refusals that say where; the batch resent
   whole, 2026-11-05), #113 (a test's class stub in the main root).
4. #123, the constructive member operations: the shop A/B's evidence for the thesis.
5. #109, then #121 (`move`) and #122 (`inline`), once Go and TypeScript have theirs.
6. The tools Java lacks, in parity's order until this log ranks them: #124 `add_result`,
   #125 `parameterize`, #126 `merge_duplicate_function`, #127 the type switch pair, #128
   `split` and inline class, #129 `add_field`.
7. #130, the option gaps: done 2026-11-08 (`encapsulate_field`'s compound writes, `fold` of
   instance methods, `line` on the signature tools).

Low priority as before: #12, #16, #32, #74. Not Java's to build: what Go and TypeScript take
from here (`pull_up`, `push_down`, `add_supertype`, `preserve_exceptions`, ...), parity
sections 3 and 4.

### The 2026-11-04 list

The user asked (2026-11-04) for the missing refactorings in this order: the
three recommended from the friction log (#80 with #81, #82, #83), then the
widening of existing tools (#84), extract interface and superclass (#85),
introduce constant, field and parameter (#86), lambda, anonymous class and
method reference (#87), record conversion (#88), extract class and parameter
object (#89), instance to static and back (#90), and the edit primitives
(#91). One PR per item; each item's design note decides whether it is a tool
or an option on one, as CLAUDE.md prefers. Not on the list, left for the
friction log to raise: renaming a package, encapsulating a collection.

### Why this order

From a survey of gorefactor's `docs/friction-log.md` (434 entries, 15 Oct to
2 Nov), its cost history, roadmap and TypeScript notes, checked on 2026-11-02:

- The log's counts are mentions, not gaps: after Oct 19 most entries say the
  work went through the tools, so a tool built early is mentioned because it
  was used. The order follows what changed the share of edits made through the
  tools and the cost per step (cost history, section 5), and the entries that
  name a gap, not raw counts.
- Edits first, then batching: the two jumps in the share of work done through
  the tools came the day the edit tools came (Oct 18) and the day batch, check
  `"end"` and references came (Oct 19).
- Loading before edits: the TypeScript spike's first finding was that a rename
  over one loaded project missed test files, and was caught only by the gate.
  This repo's gate cannot resolve its own dependencies yet.
- Mechanisms over guidance: smaller results, checked arguments, suggested
  ranges and stubs helped fresh models; more tools, longer descriptions, a
  coaching tip and the read guard did not.
- Gaps that were compositions of existing tools (moving a field, the 34 calls
  of `set`) are listed in gorefactor's `docs/derivations.md`; check there before
  adding a tool.

## Gate: how the type check works

Edits are written to disk, the files whose errors can have changed are parsed
again, and the new compile errors are compared with the old ones by file and message (not line,
so moving code does not count). Any new error means the file is restored and the
edit refused. The gate parses every file in one batch (`Jdt.parseAll`, one JDT
environment for the whole set), and reuses the last count when every file's
text is what it saw, so an edit's count after is the next edit's count before.
On Commons Math (990 files, 224k lines) one edit cost about 2 s (roadmap #10).

The check after an edit is narrowed (roadmap #56): each file's `Headers` (for
each simple name it declares, a hash of the declarations' text up to their
bodies, with the package and the imports that text names) are kept with the
count, members keyed by their type (`Shop#total`), with the type names each
header mentions. Only the changed files are parsed again, plus: the files of
every type that is "changed" (a type whose header or member changed, a type
whose header names a changed type, as a subtype does, and the type of a member
whose header names one), repeated until nothing grows; and the files that
mention, as a whole identifier, a name any of those touched, or write `new T`
of a changed type (an anonymous subclass). A body change re-checks one file.
On Commons Math, one edit takes 64 ms, a field rename in 10 files 1.3 s (114
files re-checked) and `add_parameter` 1.9 s (124 files). `GoldenTest`
compares every step's count with a fresh one; `NarrowCheckStressTest` makes
random declaration edits to a real project and compares each (roadmap #16):
a first version, which followed names only, missed three of 60 on Commons
Math, all through inheritance, and the rule above missed none of 240 there
and 100 on Commons Lang.

What is counted is every error JDT reports but one: in a named module, a sealed type's permitted
class in another package (roadmap #180, Decisions). A count made with a `module-info.java` and
one made without differ in files that did not change, so the narrowed check counts from scratch
when one appears or goes.
