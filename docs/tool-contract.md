# Tool contract

Status: draft, 2026-11-02. This is the contract the Java server should share with
gorefactor. It was written from gorefactor's status page, not from its code, so
check each point against `tychofreeman/go-refactor-mcp` (README "Status" and
"Roadmap", `CLAUDE.md`) before relying on it.

## Principles

- **Few tools, small results.** Each tool does one job. Results report what
  changed, not the whole file.
- **Refusals explain the next step.** A refusal names the reason and what to try
  instead, in plain words.
- **Do dependent work in one call.** `batch` runs several steps as one unit and
  rolls back as a whole.
- **1-based lines.** Columns are 1-based. Paths are relative to the project root.

## Tools (planned, by gorefactor's names)

Names and groups follow gorefactor. A tool is added here only when it is
implemented or being implemented.

| Group | Tools |
|---|---|
| Simplify | `rename`, `extract_variable`, `inline_variable`, `extract_function`, `inline_function`, `invert_if`, `remove_else` |
| Prepare a change | `add_parameter`, `remove_parameter`, `move_parameter`, `change_parameter_type`, `method_to_function`, `function_to_method`, `extract_struct`, `remove_dead_code`, `move_statement_up`, `move_statement_down`, `move_declaration` |
| Edit | `create_file`, `replace_statements`, `insert_statements`, `add_declaration`, `replace_expression`, `replace_text`, `add_field`, `add_case`, `add_element`, `create_from_usage` |
| Inspect | `inspect` (outline with complexity, nesting and side effects per method) |
| Multi-step | `batch` |

Java names may differ where the Java language needs it (for example, `add_field`
on a class, or `add_case` for a `switch`). Record any difference in this file:
`extract_method` (`extract_function`). `rename` has gorefactor's name.
`move_type` has no gorefactor counterpart: Go has no nested types.

## `inspect` (built)

Text, in gorefactor's format (decided 2026-11-02: copied, not measured).

- `file`: a header (`<file>  (package <pkg>, <n> lines)`), then every line
  numbered, except that each method, constructor and initializer body with two
  inner lines or more is replaced by `… lines a-b   [n lines]`. Bodies of
  methods in nested types are elided the same way. gorefactor also reports each
  body's complexity, nesting and side effects; not yet here.
- `file` + `start_line`/`end_line`: those lines; `end_line` defaults to, and is
  clamped to, the end of the file.
- Refused: a path that is not a `.java` file under the root; a file that does
  not exist (with the Java files in that directory); `start_line` past the end
  (with the line count); an unknown argument (with the arguments it takes).
- Not yet: gorefactor's no-argument overview, `function`, `targets`, `names`
  and `uses`.

## `rename` (built)

gorefactor's `rename`, for Java (roadmap #9, 2026-11-03; it replaced
`rename_local`, the user's choice over adding `rename` beside it or widening
`rename_local`).

- Arguments: `file`, `line` (the declaration or any use), `name`, `new_name`;
  `column` (1-based) to pick one of two meanings on the line. `name` may also
  pick a meaning: `Counter.count` (a member of Counter, or `Outer.Inner.count`)
  or `local count` (a local variable or parameter). gorefactor's `function` in
  place of `line` is not built.
- What changes: a local or parameter within the method (or initializer, or
  field) that declares it, its `@param` tag included; a field everywhere in
  the project, `{@link T#f}` included; a method with every project
  declaration it overrides or that overrides it, its calls, method
  references and Javadoc references; a type with its constructors, imports and
  Javadoc references, and the file of a top-level type (`Plant.java` becomes
  `Herb.java`; one `undo` moves it back). Prose in comments is not changed.
- Result: `renamed total to runningSum in <file>: <n> occurrences, lines a,
  b` for a local; `renamed the field Item.price to cost in 2 files: 4
  occurrences, a/Cart.java lines 5, 10; a/Item.java lines 4, 7`; `renamed the
  method Square.area() and Shape.area() to surface in ...`; `renamed the type
  com.example.Plant to Herb, and a/Plant.java to a/Herb.java, in ...`.
- Refused: the name not on the line (with the line's text); a name with two
  meanings there, each named with its column and the `name` that picks it
  ("names 2 declarations: the field Counter.count (column 10) and the parameter
  count (column 18). Pass name "Counter.count" or "local count", or
  column."); a declaration of a library; a method that overrides a library
  method; a type parameter (not built); an invalid new name; a new name
  another variable in the method uses, the type already has as a field, method
  with the same parameter types, or member type, or whose file exists; a field
  rename that a local of the new name would hide at an unqualified use; an
  edit that would add compile errors (rolled back, with the errors).

## `move_type` (built)

Arguments: `file` (the Java file that declares the type) and `name`
(`Outer.Inner`, or deeper, as written in that file).

Makes a static member type (a class declared `static`, or an interface, enum
or record) top-level in its own file, `Inner.java` beside `file`, in the same
package:

- the declaration moves with its Javadoc, unindented, without `static`,
  `private` or `protected`; the new file has the package line and the outer
  file's imports that the moved text uses, and the outer file drops imports
  only the moved text used;
- every reference in the project follows: `Outer.Inner` becomes `Inner`, a
  fully qualified `a.Outer.Inner` becomes `a.Inner`, `import a.Outer.Inner`
  becomes `import a.Inner` (removed in the same package), and a file in another
  package that wrote `Outer.Inner` gets `import a.Inner`, and loses an `import
  a.Outer` it no longer uses; one that wrote `Inner` unqualified, reaching it
  through a type it inherits, gets `import a.Inner` too;
- a member type of an interface (public without saying so) is written
  `public`;
- inside the moved type, unqualified uses of the outer type's static methods,
  static fields and other member types are qualified with `Outer.`, and those
  the outer type (or a type enclosing it) inherits with the declaring type's
  name (`Base.Helper`), keeping its import; what the moved type reaches
  through its own supertypes is left alone;
- a private member that the moved type uses from the outer file, or that the
  outer file uses from the moved type, becomes package-private (the user's
  choice, 2026-11-03, over refusing with their names).

Result: `moved Shop.Line to com/example/Line.java (18 lines), with 3 references
in 3 files: com/example/Receipt.java line 4; com/example/Shop.java line 7;
com/example/report/Report.java line 3; widened Shop.label(String) and
Line.price to package-private`. Lines are those of the files before the move.

Refusals: a top-level type ("Shop is a top-level type already ... name it as
Shop.Line"); a type not found; an inner (non-static) class ("Make it static
first, passing what it uses from Shop, then move it"); a file of the new name
that exists ("Rename Shop.Line first, then move it"); a move that adds compile
errors, with them.

## `pull_up` (built)

Arguments: `file` (the Java file that declares the class), `name` (`Sub.method`,
the class and the method, both as written in that file; the class is top-level),
and an optional `to` (the simple name of a direct supertype).

Moves a method or a field from `Sub` to its direct superclass, or to the direct
supertype named by `to`: the declaration, with its Javadoc, is removed from `Sub`
and added at the end of the supertype's body; imports are fixed in both files.
A name that is both a field and a method is refused; a field with other variables
in its declaration is refused (move one variable per declaration).
Calls need no change, since `Sub` inherits the method.

- Refused: no superclass and no `to`; `to` that is not a direct supertype; a
  supertype outside the project's sources; a method the supertype already
  declares (the same signature); a bare name that matches several overloads,
  which are listed (name one with its parameter types, `Sub.hello(int)`); a
  class that is not top-level.
- Type-checks the project: a method that uses what only `Sub` has is rolled
  back and refused, with the gate's "would add" wording.

## `push_down` (built)

Arguments: `file` (the Java file that declares the class) and `name`
(`Sub.method`, both as written in that file; the class is top-level).

Moves a method or a field from `Sub` to each direct subclass of `Sub` in the
project: the declaration, with its Javadoc, is removed from `Sub` and added at the
end of each subclass's body; imports are fixed in every file touched.

- Refused: no direct subclass in the project; a subclass that already declares
  the member; a method that overrides a method of a supertype (`@Override`, or a
  superclass that declares the same signature); a bare name that matches several
  overloads, which are listed (name one with its parameter types); a class that is
  not top-level.
- Type-checks the project: a caller that uses the method through `Sub` (say
  `Sub s; s.hello()`) no longer compiles, so the move is rolled back and refused.

## Edits (built)

Edits change behavior on purpose and are held to the same gate: a `.java` file
must parse, and the project must type-check no worse after, or nothing changes.
Results name the file and lines and never echo content.

Stubs (roadmap #21): before refusing, the gate (every edit, and a batch checked
at its end when one of its steps is an edit) declares what the new code uses and nothing declares, then checks
again, for up to five rounds (a new class, then its members). A refactoring
(`rename`, `move_type`, the signature tools, `extract_method`) gets none: it
changes no behavior, so a name its output leaves unresolved is its own mistake,
and is refused with the errors (2026-11-03, after a `move_type` bug passed
the gate behind an empty stub class):
- a method of a project class: `x.m(a)` on x's type, `T.m(a)` static on T,
  `m(a)` on the enclosing type (static in a static context). Parameters take
  the arguments' types, and their names when they are variables or getters.
  The result type comes from the use: void as a statement; boolean as a
  condition; the type of what it is declared as, assigned to, returned as,
  compared or combined with, or passed as (for `assert*`, the other
  argument's); else Object;
- a constructor of a project class, and `T() {}` beside it when T had only the
  implicit one;
- a class nothing declares (a type, or the receiver of a static call
  `Tasks.run()`), in the using file's package under the first source root, as
  `public class T {}`.
A call that is a lambda's body, or returned from one, gets the result type of
the method the lambda implements.
The body is `throw new UnsupportedOperationException("not implemented");`,
which `add_declaration` replaces. Members are public only when the type is in
another package. Not declared: methods of interfaces or library types, fields,
and a method or constructor the edit removed. The result ends
`; declared stubs Cart.total(), Cart.add(String, int) in com/example/Cart.java;
class Basket in ...`.

Imports (roadmap #20): `replace_text`, `add_declaration` and `replace_declaration`
on a `.java` file add an import for each simple type name the edit left
unresolved when one class has that name (this project's sources, the classpath,
the JDK; `java.awt`, `javax.swing`, `sun.*`, `com.sun.*`, `jdk.*` only when
nothing else has it), in order among the file's imports, and remove single-type
imports the edit left unused (an import already unused stays). A name with
several candidates is refused with them; the optional `imports` argument (fully
qualified names) chooses. An unqualified call to no method is imported
statically from the one class the file already imports a static member from
that has a static method of that name (`assertFalse` beside `import static
...Assertions.assertEquals`), before stubs are declared. Results end with
`; imported a.B, c.D, static e.F.g` and `; removed import e.F`, and their line numbers count the import lines. `create_file`
content is a whole file and keeps its own imports.

The server runs one call at a time: calls sent together in one turn run in the
order they arrive, so a call that needs an earlier one's result can follow it
in the same turn only if it is sent after it.

- `create_file`: `file`, `content`, `overwrite`. A `.java` file must declare the
  package its directory implies under a source root. Result: `created <file>
  (<n> lines)`.
- `replace_text`: `file`, `old`, `new`, `all_occurrences`. `old` must occur once
  unless `all_occurrences`; refused with the lines it occurs on. Result:
  `replaced <n> occurrence(s) in <file>, line(s) ...`. gorefactor's `files`,
  `function` and `append` are not built.
- `add_declaration`: `file`, `content` (members), `type` (`Outer` or
  `Outer.Inner`; default the file's only top-level type), `after` / `before` (a
  member name). Members are re-indented to match. A method with the same name
  and parameter types whose body is only `throw new
  UnsupportedOperationException("not implemented")` is replaced. Result: `added
  <members> to <type> in <file>, lines a-b` or `replaced the stub <m> ...`.
- `replace_declaration`: `file`, `name` (`Type`, `Type.member`, `member`; with
  `(parameter types)` for an overload), `content` (empty deletes, with the
  Javadoc; deleting a file's only top-level type deletes the file, "deleted Old
  and its file com/example/Old.java (was lines 5-8)", and a type still used is
  refused rather than declared again as a stub). The old Javadoc stays unless content has its own. Refused: several
  members of that name (with their signatures), no such member (with the
  members there), a field declared with others in one statement. gorefactor's
  `line`/`column` addressing and `imports` are not built.

Refusals come back as an error result whose text starts `refused: `. Other
failures start `engine error (not a refusal; ...)`.

## Signature changes (built)

`add_parameter`, `remove_parameter`, `move_parameter` (roadmap #22). Each takes
`file` (the declaring file) and `name` (as `replace_declaration`: `Type.member`,
`member`, `m(int, String)` for an overload; a constructor by its type's name),
and `parameter`:
- `add_parameter`: `type`, `value` (a Java expression each call passes),
  optional `before` (default last), optional `imports`.
- `remove_parameter`: also deletes the parameter's `@param` tag.
- `move_parameter`: optional `before` (default last).

The change covers the method's family: every project declaration that overrides
it or that it overrides, transitively, joined by binding key across files (an
interface method and all its implementations). Calls: method invocations
(`super.m` too), `new T(...)`, `this(...)`, `super(...)`, enum constants. Each
changed file has its imports fixed; the whole change passes the gate once.

Refusals: a family member that overrides a library method ("Name.compareTo(Name)
overrides compareTo in java.lang.Comparable, which is not the project's"), a
method reference to it, varargs, a parameter or `before` it does not have;
remove: a lambda that reads the parameter ("the lambda (com/example/Calc.java,
line 5) reads b"). A lambda that implements a family member changes with it
(roadmap #57): its parameter list is written again, with the new parameter's
name (and type when its parameters are typed), without the removed one, or in
the new order; files with `->` are searched when the family has an abstract
interface method, since a lambda need not name it;
add: a body that reads an unqualified field of the new name; remove: a body
that reads the parameter, an argument that may have side effects (anything but
names, literals, `this`, field accesses, lambdas and operators over those);
move: two such arguments whose order would change; nested calls whose
edits overlap.

Result: `added int capacity to Cart.Cart(String) in 1 declaration and 3 calls
(a.java, b.java)`, then any import notes per file.

## `extract_method` (built)

Java's name for gorefactor's `extract_function` (roadmap #23).

- Arguments: `file`, `start_line`, `end_line` (inclusive), `name`, optional
  `doc` (Javadoc text without `/** */`). Blank lines at either end of the range
  are dropped.
- The lines cover whole statements of one statement list (a body, a `{}`
  block, a `case`, a lambda's block); comments and blank lines may be in the
  range. The list is the outermost one the range falls in.
- The new method is private, after the member the statements are in (in the
  same type, an anonymous class's included), static exactly when that member
  is. Its parameters are the locals declared outside the statements that they
  use, in order of first use, typed from their bindings. Its result is the one
  local the statements declare, or assign, that later code uses (or that they
  assign inside a loop it outlives); the call is `T x = m(...)` or
  `x = m(...)`. It declares the checked exceptions the statements throw and do
  not catch, and the enclosing method's type parameters.
- `return`: in a method with a result, the call is `return m(...)` and the
  statements must return on every path; in a void method, only statements
  that end the body.
- The text moves, comments included, re-indented (text blocks as they are).
- Refused: a range that cuts a statement (with the statement lines of that
  list), no statements, not in a body, two or more results (named), a return
  and a result, a `break`/`continue`/`yield` whose target is outside, a return
  on some paths only, a return mid-way through a void method (with the range to
  the end of the body), `this(...)`/`super(...)`, an assignment to a final
  field ("line 9 assigns the final field upper, which only a constructor or
  initializer may do"), a name a method of the type or a supertype has; then
  the gate.
- Result: `extracted lines 9-12 of a/B.java into sum(List<Integer> values),
  returning total, at lines 14-20; the call is line 9`, a note past five
  parameters, then import notes.

## `batch` (built)

- `steps`: a list of `{tool, args, id?}`, each a tool and the arguments it takes
  alone; `check`: `"each"` (default) or `"end"`.
- Line arguments (`line`, `start_line`, `end_line`) are as read before the
  batch; the batch moves them past earlier steps' edits. A line an earlier step
  rewrote with a different line count is refused, naming the step.
- `"$prev.start"`, `"$prev.end"`, `"$2.end"`, `"$id.start+1"` name the first or
  last non-blank line an earlier step changed in that step's file.
- A refused step leaves nothing changed; the refusal says `step k of n (tool)
  refused: ...`, and suggests check `"end"` when the step failed the type check.
  With `"end"` the gate runs once after the last step.
- Result: `batch of n steps:` then each step's result, numbered; with `"end"`, a
  last line `checked at the end: no new compile errors`.
- `batch` and `undo` cannot be steps.

## `undo` (built)

- Reverts the last change made through the tools in this server session: one
  call, or a whole batch. Call again to step back further.
- Kept in memory per server process (open question 3: not on disk).
- Refused when a file it would restore changed since; nothing is undone.
- Result: `undid <tool>: restored a, b; removed c`.

## Command line (built)

`bin/jrefactor` (class `Cli`) runs one tool per process with the server's
definitions, arguments and results, as gorefactor's `cmd/refactor` does:

    jrefactor list [tool...]
    jrefactor [-C dir] <tool> key=value...
    jrefactor [-C dir] <tool> '<json>' | @file | -
    jrefactor [-C dir] undo

The project is the nearest directory with a `pom.xml` at or above `-C`
(default: the current directory). `key=value` values are typed by the tool's
schema (integer, boolean; arrays and objects as JSON). `key_file=path` gives
the text argument `key` from a file. Output is the result the model would
read, or `refused: ...`; exit status 0 done, 2 refused, 1 error. The undo
stack (the newest 20 changes) is kept between runs in
`$XDG_CACHE_HOME/jrefactor` or `~/.cache/jrefactor`, one file per project,
beside the Maven classpath (used while `pom.xml` is unchanged and every entry
exists) and the last type check (used while every file's hash and the
classpath are the same).
Not built: gorefactor's `-n` preview and `check`.

## Results

- Applied change: the file or files touched, and a compact diff or a count of
  changed locations. No context lines by default.
- Renames: list the locations, not full diffs.
- Refusal: `refused` with a reason and a suggested next step.
- Errors from the engine (JDT failures, missing project): a distinct error kind,
  never a refusal.

## Refusal wording

Refusals are part of the model's experience. Keep them specific ("this selection
starts inside a statement; select whole statements, lines 12 to 15") and
actionable. Avoid generic messages such as "unsupported".

## Tools that changed for the better (gorefactor evidence)

- Argument checks up front, suggested ranges, and retry with check `"end"` cut
  wasted calls.
- Stubs and `add_declaration` replacing stubs let edits start from code that does
  not compile yet.
- Coaching tips inside results did not help and were removed. Do not add them.

## Open
- Java-specific refusals (checked exceptions, overloads, generics) need their own
  wording once the first refactorings exist.
