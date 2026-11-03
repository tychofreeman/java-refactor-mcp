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
`rename_local` (a narrower `rename`), `extract_method` (`extract_function`).

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

## `rename_local` (built)

Java's name for a narrower `rename`: local variables and parameters only, and
uses within the method (or initializer, or field) that declares them.

- Arguments as gorefactor's `rename`: `file`, `line` (the declaration or any
  use), `name` (the current name), `new_name`; `column` (1-based) only when the
  name is on the line with two meanings. gorefactor's `function` in place of
  `line` is not built.
- Result: `renamed <name> to <new_name> in <file>: <n> occurrences, lines a, b`.
- Refused: the name not on the line (with the line's text); a field, or a name
  that is not a variable; two variables of that name on the line (with their
  columns); a `column` not on `name`; an invalid new name; a new name another
  variable in the method uses; an edit that would add compile errors (rolled
  back, with the errors).

## Edits (built)

Edits change behavior on purpose and are held to the same gate: a `.java` file
must parse, and the project must type-check no worse after, or nothing changes.
Results name the file and lines and never echo content.

Stubs (roadmap #21): before refusing, the gate (every edit, and a batch checked
at its end) declares what the new code uses and nothing declares, then checks
again, for up to five rounds (a new class, then its members):
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
qualified names) chooses. Results end with `; imported a.B, c.D` and `; removed
import e.F`, and their line numbers count the import lines. `create_file`
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
  Javadoc). The old Javadoc stays unless content has its own. Refused: several
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
  the end of the body), `this(...)`/`super(...)`, a name a method of the type
  or a supertype has; then the gate.
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
