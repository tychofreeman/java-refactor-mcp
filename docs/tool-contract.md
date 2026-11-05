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

## Exceptional behavior (`preserve_exceptions`, every refactoring)

A refactoring keeps every effect of the normal flow; it does not keep
exceptional behavior (where an exception is thrown, or whether one is) by
default (`docs/design.md`, Decisions).

- Argument: `preserve_exceptions` (boolean, optional) on every refactoring.
  When true, the tool keeps exceptional behavior where it can, or refuses with
  "<the change>. preserve_exceptions is set, so nothing was changed: <how to
  keep it>".
- Result: without the flag, each change to exceptional behavior the tool
  knows of is listed in a part of the result of its own: a second text block
  in MCP, a section after a blank line on the command line, starting
  "exceptional behavior changed (pass preserve_exceptions=true to keep it, or
  to be refused):", one change per line.
- What is detected so far (syntax only): `move_member`, a receiver that may be
  null (kept with `java.util.Objects.requireNonNull(receiver)`);
  `inline_variable`, an initializer that may throw moved past a statement
  that changes state (refused); `move_statement`, a statement that may throw
  swapped with one that changes state (refused). "May throw" is a dereference
  of a variable or expression, an array element, an integer `/` or `%`, a
  reference cast, a call or a new object.
- Also detected (roadmap #75, 2026-11-05): `move_statement`, a statement that
  may throw swapped with a guard (a statement that may return, or break,
  continue or yield out of itself); `extract_variable`, an expression that may
  throw taken out of a try's resources, out of its `catch` and `finally`.
- Also detected (roadmap #75, 2026-11-04): `inline_function`, an argument that
  may throw whose parameter the body does not use, or uses only in a branch, a
  lambda or an anonymous class (it would throw only sometimes, or never);
  `inline_variable`, an initializer that may throw moved into an `if`, a loop, a
  `?:` or `&&`/`||` branch, a lambda, a `switch` or a `try` (it would throw there
  only, if at all, or be caught).

## Moved code keeps its meaning (`NameCheck`)

Roadmap #77, 2026-11-04. `inline_function`, `inline_variable`, `convert_lambda`,
`pull_up`, `push_down` and `move_member` check, before writing, that each name in the text they write resolves to a
declaration it resolved to where the text came from (the body, the arguments, the
initializer, the lambda). A name the moved text declares itself, or one it did not
have (a qualifier the tool added), is not checked. Refused: "inlining Cart.total
would make count at line 11 of <file> mean the field count of an anonymous class,
not what it meant where it was written: a closer declaration of that name hides it
there. Rename one of them, or qualify it, first."

## Names shared with gorefactor

Tools keep gorefactor's names where the Java tool does the same thing:
`inspect`, `rename`, the signature changes, `extract_variable`,
`inline_variable`, `inline_function`, `invert_if`, `remove_else`,
`move_statement`, `create_file`, `add_declaration`, `replace_declaration`,
`replace_text`, `batch` and `undo`. Where Java differs, the name says so:
`extract_method` is gorefactor's `extract_function`; `move_member` covers
gorefactor's `method_to_function`; `change_type` is for fields. `move_type`,
`pull_up`, `push_down`, `add_supertype` and `encapsulate_field` have no
gorefactor counterpart (Go has no nested types or inheritance). gorefactor's edit
primitives are built with its names (`replace_expression`, `insert_statements`,
`replace_statements`, `add_case`, `add_element`, roadmap #91).

Planned (the user, 2026-11-04, roadmap #80-#91): `move_type` to another
package, `move_file`, a parameter or return type change, safe delete,
widened `inline_function`, `extract_method` and `rename`, extract interface
and superclass, introduce constant, field and parameter, lambda / anonymous
class / method reference, class to record, extract class and parameter
object (gorefactor's `extract_struct`), instance method to static and back
in place (`method_to_function`, `function_to_method`), and gorefactor's edit
primitives (`replace_expression`, `insert_statements`, `replace_statements`,
`add_case`, `add_element`).

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
  method; a type parameter renamed to a type its class or method already names
  ("String is a type that Box already names (line 5); renamed, T would hide
  it"; type parameters are renamed in their declaration, Javadoc `@param <T>`
  included, roadmap #84, 2026-11-04); an invalid new name; a new name
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

With `package` (roadmap #80, 2026-11-04), `name` is the file's only top-level
type, and it moves to that package in the same source root: the file moves
(`a/Shop.java` to `b/Shop.java`) with its package line, every `import a.Shop`
(and `import a.Shop.Line`, static imports included) and qualified `a.Shop`
follows, and files of package `a` that name it gain `import b.Shop`. A
package-private declaration that a use crosses the new boundary with becomes
public: the type and its members used from another package, and what of
package `a` the type uses. Result: `moved Shop to package b (b/Shop.java),
with N references in M files: <file> <lines>; ...; widened Helper, Helper.TAX
and Shop.price() to public`. Refusals: a name that is not a package name; a
file with several top-level types ("move the others to files of their own
first"); the default package ("no other package can import" it); the package
it is in; a target file that exists ("Rename one of the two types first").

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
  declares (the same signature); a method that overrides a concrete method the
  supertype inherits (`Root.name()` for `Base extends Root`: `Base` and its other
  subclasses would run the pulled body); a bare name that matches several overloads,
  which are listed (name one with its parameter types, `Sub.hello(int)`); a
  class that is not top-level.
- Type-checks the project: a method that uses what only `Sub` has is rolled
  back and refused, with the gate's "would add" wording.

## `extract_supertype` (built)

Roadmap #85, 2026-11-04. Arguments: `file`, `name` (a top-level class, not
generic), `supertype` (the new type's simple name), `kind` (`interface` or
`class`), `members` (names, with parameter types for an overload).

- `kind=interface`: `<Supertype>.java` in the class's package declares the
  signatures of the named methods (public instance methods only, with their
  `throws`), and the class gains `implements <Supertype>` (as `add_supertype`).
- `kind=class`: `<Supertype>.java` extends the class's old superclass, if any,
  and takes its place in the class's `extends`; each member moves into it as
  `pull_up` moves it (fields and methods), with `pull_up`'s refusals.
- One call: a refusal at any step changes nothing. Result: `extracted interface
  Pricing (com/example/Pricing.java) from Flat with price; Flat implements it`,
  or `extracted class Priced (...) from Flat with base and price; Flat extends it`.
- Refused: an interface, a generic class, a file of the new name that exists, a
  member not found, a method that is not public or is static (interface).

## `convert_lambda` (built)

Roadmap #87, 2026-11-04. Arguments: `file`, `line` (where it starts), `column`
(when several start there), `to` (`lambda`, `anonymous`, `method_reference`).

- Lambda to method reference: a body that is one call passing the parameters in
  order: `s -> s.length()` is `String::length`, `x -> f(x)` is `this::f` or
  `Type::f`, `x -> new T(x)` is `T::new`, `x -> out.println(x)` is
  `out::println` when `out` is a local that does not change or a static final
  field.
- Method reference to lambda: `(s) -> s.length()`, the parameters named after
  their types' first letters, unused in the member; a receiver that may change
  (a field, a call) is refused, since the reference evaluates it once.
- Anonymous class to lambda: a functional interface's anonymous class with its
  one method; its body re-indented. Lambda to anonymous class: `new T() {
  @Override public R m(P p) { ... } }`, from the lambda's resolved types.
- Refused: `this`, or a bare call of a method of the interface, of its supertypes (an
  inherited default, roadmap #94) or of `Object`, in
  the code that moves (it would mean another object); an anonymous class with
  fields or other methods; a type with a wildcard; a form that does not compile
  there (the gate). Result: `converted the lambda at line 7 of <file> to
  String::length`.

## `convert_to_record` (built)

Roadmap #88, 2026-11-04. Arguments: `file`, `name` (a top-level class),
`value_semantics` (boolean).

- The instance fields (all `final`, no initializers) become the components, in
  declaration order; the one constructor, which only does `this.f = p;` for each
  field in order, is the canonical one and is removed; an accessor `x()` that is
  not public becomes public; every other member stays (getters too), and the
  class's modifiers but `final` and `static`, its type parameters and its
  `implements` carry over.
- A record generates value-based `equals`, `hashCode` and `toString` for those
  the class does not define, which changes behavior: refused without
  `value_semantics=true` ("Pass value_semantics=true if that is wanted, or
  define them first").
- Refused also: a superclass; a class of the project that extends it; an
  abstract class; a field that is not final or has an initializer; an instance
  initializer; other constructors. Result: `converted Point to a record (x, y)
  in <file>; it now has the record's equals, hashCode and toString; made x()
  public`.

## `extract_struct` (built)

Roadmap #89, 2026-11-04, gorefactor's name. Arguments: `file`, `name` (the method,
as the signature tools take it), `parameters` (next to each other), `struct` (the
new record), `parameter` (the new parameter's name), `imports`.

- `public record Size(int width, int height) {}` goes in a new file beside the
  method's class; the parameters become `Size size` in every declaration of the
  family; each body reads `size.width()` where it read `width`; every call passes
  `new Size(3, 4)` of its arguments. Result: `grouped width and height of
  Shipping.cost(int, int, int) into Size size (com/example/Size.java) ...`.
- Refused: parameters not next to each other (their arguments would run in
  another order; move them first); a body that assigns one; a family lambdas
  implement; a varargs parameter; a type the record in its own file cannot name (a
  type variable, a private or local type: "the type of shift uses the private type
  SchurTransformer.ShiftInfo", roadmap #95); a body that already names the new parameter; a
  record file that exists.
- Extract class (`fields` and `field` in place of `parameters` and `parameter`;
  `name` is the class): the named instance fields move into a new package-private
  class `<struct>` in a file of its own, their Javadoc and modifiers but
  `private` kept; the class holds one in `private final <Struct> <field> = new
  <Struct>();` in the first field's place, and every use becomes `field.x`.
  Refused: a field used outside its class ("street is used in Other.java line 5
  ... Encapsulate them first"), an initializer that reads anything but constants
  (new objects of constants are fine), a final field without an initializer, a
  static field, a holder name the class already uses. Result: `moved street and
  city of Person into Address (com/example/Address.java), held in home`.

## `function_to_method` (built), and `move_member` in place

Roadmap #90, 2026-11-04 (gorefactor's `method_to_function`, `function_to_method`).

- Instance to static in place: `move_member` with `to` the method's own class; the
  method becomes `static`, takes the instance as `receiver`, and calls pass it
  (`Price.charge(this, a)`), as it does for another class.
- `function_to_method`: `file`, `name` (a static method whose first parameter is
  of its own class), `imports`. `static int area(Shape s, int k)` becomes `int
  area(int k)`, `s` becomes `this` in the body, and `Shape.area(x, 3)` becomes
  `x.area(3)` (`area(2)` where `this` was passed). A first argument that may be
  null now fails at the call, not where the body used it: reported as a change to
  exceptional behavior, and refused under `preserve_exceptions`. Refused also:
  not static, a first parameter of another type or varargs, a body that assigns
  it, lambdas or method references of it, an instance method of the new signature
  in a subclass ("Square.area(int) would then override it") or a supertype,
  libraries included (roadmap #92).

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
- `move_file` (roadmap #81, 2026-11-04, gorefactor's name): `file` (a file or
  directory), `to`, `copy`, `delete` (instead of `to`). Text files that are not
  Java under a source root; each goes through the journal, so `undo` puts it
  back, and directories a move or delete empties are removed. Result: `moved
  docs/a.md to notes/a.md`, `copied ...`, `deleted cases/one (2 files)` (a
  directory names its file count). Refused: a path that does not exist, a `to`
  that does or that is inside `file`, Java under a source root ("Use move_type
  (with package)"), a file that is not UTF-8 ("Move this one with git mv").
- Edit primitives (roadmap #91, 2026-11-04, gorefactor's names), each with
  optional `imports`, through the gate with stubs and imports as every edit:
  - `replace_expression`: `file`, `line`, `expression` (exact text), `column`,
    `new`. Result: `replaced 0.2 at line 5 of <file>`.
  - `insert_statements`: `file`, `line` (a statement directly in a block),
    `where` (`before` or `after`, default after), `content`, re-indented to that
    statement. Result: `inserted 1 line after the statement at line 5 of <file>`.
  - `replace_statements`: `file`, `start_line`, `end_line` (whole statements of
    one block), `content` (empty deletes). Refused: an `end_line` that does not
    end a statement of that block, with the lines that do. Result: `replaced lines
    5-6 of <file> with 1 line` or `deleted lines 5-6 of <file>`.
  - `add_case`: `file`, `line` (a switch statement or expression), `content`;
    before `default`, else at the end, indented as the other cases.
  - `add_element`: `file`, `line` (an array initializer or an enum), `content`;
    after the last element (on a new line when the elements are one per line).
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
  refused rather than declared again as a stub). A deletion is a safe delete
  (roadmap #83, 2026-11-04): refused while anything else uses it, "Price.rate()
  is used in com/example/Shop.java line 5. Remove or change those uses first,
  then delete it." (constructors by their calls too), and for a method that
  overrides a concrete one ("Sub.name() overrides Base.name(): deleted, its
  callers would run Base's instead ..."). The old Javadoc stays unless content has its own. Refused: several
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
  `x = m(...)`. Several such locals (roadmap #84, 2026-11-04, the user's choice
  over refusing): the method returns `new BothResult(x, y)`, a `private record
  BothResult(int x, int y) {}` declared after it, and the call is
  `BothResult bothResult = both(a);` then `int x = bothResult.x();` for each.
  Refused then: a record name the type already has, a holder name the code
  around already uses. It declares the checked exceptions the statements throw and do
  not catch, and the enclosing method's type parameters.
- `return`: in a method with a result, the call is `return m(...)` and the
  statements must return on every path; in a void method, only statements
  that end the body.
- The text moves, comments included, re-indented (text blocks as they are).
- Refused: a range that cuts a statement (with the statement lines of that
  list), no statements, not in a body, a return
  and a result, a `break`/`continue`/`yield` whose target is outside, a return
  on some paths only, a return mid-way through a void method (with the range to
  the end of the body), `this(...)`/`super(...)`, an assignment to a final
  field ("line 9 assigns the final field upper, which only a constructor or
  initializer may do"), a name a method of the type or a supertype has; then
  the gate.
- Result: `extracted lines 9-12 of a/B.java into sum(List<Integer> values),
  returning total, at lines 14-20; the call is line 9`, a note past five
  parameters, then import notes.

## `extract_variable` (built)

Names an expression: a declaration is inserted just before its statement, and
the name is used in its place. gorefactor's `extract_variable`, with the same
arguments.

- `file`, `line` (1-based, the line the expression starts on), `expression`
  (its exact source text), `name`, and `column` (1-based, only when the text
  occurs twice on that line).
- Result: `extracted <expression> as <name>, declared at line N of <file>`.
- Checked by the behavior gate (`Behavior`, roadmap #96): refuses an expression
  with an effect that would run in another order (an effect earlier in its
  statement) or at another time (out of a `?:` branch, right of `&&`/`||`, a
  loop, a lambda); one in a loop condition, update or body that reads a
  variable; one that may throw out from under its guard (right of `&&`/`||`, a
  branch) or out of a try (body or resources) that catches or finishes; one
  that reads state an earlier effect could change, or that an earlier write
  assigns. Allowed: the first effect of a statement (`f(p.bin(), p.bin())`
  names the first `p.bin()`, roadmap #99), and pure code from a branch
  (`any ? price * qty : 0`). Also refuses: assigned to; a statement not
  directly in a block; a type that does not resolve; a new name that a field of the
  enclosing class or its superclasses already has, which the local would hide.
  `all_occurrences` and `pointer` are not built yet.
- `as` (roadmap #86, 2026-11-04): `constant` adds `private static final T NAME =
  expr;` as the type's first member, `field` adds `private final T name = expr;`
  (refused in a static member), and `parameter` adds a parameter to the method
  around it, as `add_parameter` does, every call passing the expression; the
  expression becomes the name. The expression must be literals and constants
  (static final fields): anything else would be evaluated at another time or
  place ("uses a local, a parameter, a field that is not a constant, or a
  call"). Refused also: a field of that name already in the type. Result:
  `introduced constant RATE (private static final double RATE = 0.2) in Price,
  in place of 0.2 at line 5 of <file>`; for a parameter, `add_parameter`'s
  result and `; 0.2 at line 5 is now rate`.

## `inline_variable` (built)

Replaces every use of a local variable with its initializer, and deletes its
declaration: the inverse of `extract_variable`. gorefactor's `inline_variable`,
with `file`, `line` (a line where the variable is declared or used), `name`,
and `column` (only when the line has two variables of that name).

- Result: `inlined <name> into N uses and deleted its declaration at line L of <file>`.
- Refuses: a field or a parameter; several variables in one declaration, or no
  initializer; a variable assigned after its declaration; an initializer with an
  effect (a call, an assignment, a new object) used more than once, moved past
  another effect, or into code that runs only sometimes, more than once or later
  (one used once, with nothing between, is inlined: `int n = s.get(); return n + 1;`);
  an initializer that reads state (a field, an element, a call's result) that a
  call or assignment between the declaration and a use could change; a declared
  type that differs from the initializer's; an initializer that reads a name a later declaration in the block
  hides at a use. `function` (inline by a function's name) is not built yet.

## `inline_function` (built)

Replaces every call of a method in the project with its body, the
arguments put in place of the parameters, then deletes the method. gorefactor's
`inline_function` takes more; this first version takes `file` (the file that
declares the method) and `name` (`Type.method`), and inlines every call.

- Result: `inlined Type.method into N calls and deleted the method in <file>`.
- Instance methods (roadmap #84, 2026-11-04): the call's receiver replaces `this`, and
  goes before each instance member the body names bare (`c.total()` with `return count
  * 2` becomes `c.count * 2`); a call with no receiver, or `this`, keeps the body as it
  is. Refused: a method a declaration in the project overrides ("is overridden by
  Sub.total(), so a call of it may run that one"), a receiver that is not fixed ("Put
  it in a variable first"), a body that uses `super`. A receiver that may be null is
  reported as a change to exceptional behavior (it now fails where the body uses it),
  and refused under `preserve_exceptions`.
- A bare field of the body that a local or parameter of the calling member hides is
  qualified, `this.count` (or the type, for a static), so it keeps meaning the field
  (roadmap #93).
- Refuses: a varargs, generic or abstract method; a body that returns
  before its last statement; a method that calls itself; a method used as
  `Type::name`; a call nested in another call's arguments; a parameter used more than
  once when its argument calls, assigns or changes a variable; a method with no calls;
  an argument with effects for a parameter the body does not use (the call would drop
  it); two arguments with effects (they would run in another order); an argument whose
  type differs from its parameter's, where the call converts (`half(3)` for `double`), but
  for a reference subtype when every use of the parameter is a call's receiver (a `String`
  for `CharSequence s` in `s.length()`, roadmap #78), or an argument of a call of a method
  with no overload of its arity in its class and supertypes, not generic or varargs (it
  resolves to the same method; 2026-11-05);
  a body that names a member of its class when a call is in another class. Each
  argument the behavior gate (below) refuses in place, or that `checkOrder` finds out
  of order, is bound to a local before the call's statement instead (roadmap #102),
  with every earlier argument that is not fixed, so they run once, in order: the
  local has the parameter's name and type, numbered when the method already uses the
  name. The move to before the statement is checked by the gate as an extract; when
  it is refused (an effect earlier in the statement), the in-place reason is the
  refusal. Each argument is checked by the behavior gate (roadmap #96) as code that
  ran just before the body and lands at its parameter's uses: one with an effect that the body drops,
  uses twice, runs after another effect, or uses where it runs only sometimes, more
  than once or later (a lambda, a `?:` branch, right of `&&` / `||`, a branch of an
  `if`, a loop, a `switch` case, a `catch`); one that reads state (a field, an
  element, a call's result) that a call in the body before its use could change, or
  that the body writes; what may throw and is dropped, or moved past a change, is
  reported; two such
  arguments, one with an effect, that the body first uses in the other order than the
  call passes them (`f(bump(), count)` with `b + a`).
- A body of several statements (roadmap #84, 2026-11-04): a void method's call statement
  becomes the body's statements, re-indented; a method ending in its one `return expr`
  must be called as all of an initializer, a `return` or an assignment's value, and its
  other statements go before that statement ("part of a larger expression" otherwise).
  Refused also: a `return` before the last statement; a body that assigns a parameter;
  a body local whose name the calling member uses.
- Not built yet: `line` and `column` addressing, `keep_function`,
  inlining one call at a time.

## `change_type` (built)

Changes the declared type of a field, so that its uses must still compile. The
first version takes `file` (the file that declares the field), `name` (`Type.field`)
and `type` (the new type as written).

- Result: `changed the type of Type.field from <old> to <new> in <file>`.
- Refuses: a name that is not a field of the top-level class; a declaration of several
  fields; a change that adds compile errors (the gate names them, nothing changes).
- A method (roadmap #82, 2026-11-04): `name` is `Type.method` (with parameter types for
  an overload) and `parameter` (whose type changes) or `return_type: true`; optional
  `imports`. Every declaration in the method's family (overrides, implementations)
  changes, and a lambda that writes the parameter's type; calls are not edited, so a
  call the new type breaks is refused by the gate with the error. Result: `changed the
  type of qty of Pricing.price(int) to long ...` or `changed the return type of ...`.
  Refuses: both options or neither with a method; a varargs parameter; a `[]` written
  after the name; a constructor's return type.
- Not built yet: locals, and fields declared with several names.

## `add_supertype` (built)

Adds a supertype to a top-level class or interface, in its header. Arguments: `file`, `name`
(the top-level type), `relation` (`extends` or `implements`, the clause the supertype goes in)
and `supertype` (as written, such as `Base` or `List<String>`).

Result: `added S as a supertype of T (relation) in file`, with `; imported ...` when the edit
adds an import. Refusals: a relation other than `extends` or `implements`; a name that is not a
top-level type of the file that resolves; `implements` on an interface; `extends` on a class that
already extends one; and the gate's "would add compile errors", naming them.

## `move_statement` (built)

Swaps a statement with the one before it (`direction=up`) or after it (`direction=down`) in the same
block. Arguments: `file`, `line` (the 1-based line the statement starts on), `direction` (`up` or
`down`), and `names_only` (optional flag; see below).

The two statements must be independent. By default (strict): they share no name that one of them
declares or writes and the other uses, and at most one has a side effect (a call, an assignment,
`++`/`--`, a `new`). With `names_only=true`, only the shared-name rule applies, so two calls may swap
order.

Result: `moved the statement at line L up|down, past the statement at line M in file`, with
`; imported ...` when an import is added. Refusals: no statement starts on the line, several do, a
return, break, continue, throw or yield (on either side), no neighbour in that direction, a shared
name, two side effects under the strict rule, a statement with a side effect and one that may leave
the block early (a `return`, or a `break`, `continue` or `yield` that leaves it: an `if` that
returns is a guard), a statement that may throw lifted above such a guard (the guard is kept, by
the policy), a call moved past a read of a field or element it could change (`int y = count;
bump();`), and the gate's "would add compile errors". Checked by the behavior gate (roadmap #96)
beyond the shared-name and two-effect rules, which stay the tool's own. A statement that may throw
swapped with one that changes state is reported.

## `invert_if` (built)

Inverts an if that has an else: the branches swap and the condition is negated. Arguments: `file`,
`line` (the line the if starts on). A comparison takes its opposite operator (`==` and `!=`, `<` and
`>=`, `<=` and `>`); a `!x` loses its `!`; any other condition is wrapped as `!(...)` (or `!x` for a
name or a call).

Result: `inverted the if at line L in file; its condition is now C`, with `; imported ...` when an
import is added. Refusals: no if on the line, several on it, an if with no else, an else if chain
(invert the inner if), an ordering comparison on a float or double (NaN), and the gate's "would add
compile errors".

## `remove_else` (built)

Removes the else of an if whose then-branch cannot finish normally; the else's statements then follow
the if, at its indentation. Arguments: `file`, `line` (the line the if starts on).

Result: `removed the else of the if at line L in file`, with `; imported ...` when an import is added.
Refusals: no if on the line, several on it, an if with no else, an if not directly in a block, a
then-branch that can finish normally (it does not end in return, throw, break, continue or yield),
and the gate's "would add compile errors".

## `move_member` (built)

Moves a method of a top-level class to another top-level class of the same file. Arguments: `file`,
`name` (`Type.method`, or `Type.method(params)` for an overload), `to` (the target class).

An instance method becomes static in the target and takes the source instance as its first parameter,
named `receiver`; every call in the project passes it (`Type.m(instance, args)`). A private method is
widened to package access (named in the result). Result: `moved Type.m to T in file; N calls rewritten
in K files`, with `; private widened to package access` when so.

Refusals: an override, a method another class overrides, a constructor, no body, a generic class, a
method reference to it, a call of itself, super, an anonymous or local class in the body, a call from
another class with no instance, a nested call inside an argument, and the gate's "would add compile
errors". A null instance that the method dereferences fails inside it, not at the call.

## `encapsulate_field` (built)

Makes a field private, with a getter and a setter, and rewrites the project's uses through them. Arguments:
`file`, `name` (`Type.field`), `getter` and `setter` (optional; default `getX` and `setX`).

A read becomes the getter call (`x` to `getX()`, `p.x` to `p.getX()`); a plain assignment statement
`x = v;` becomes `setX(v);`. A final field gets a getter only. Result: `encapsulated Type.field in file
with getX and setX; N uses rewritten in K files`.

Refusals: a name that is not a field, a declaration of several fields, a final field that is written, a
compound write (`+=`), `++` or `--`, a write inside an expression, a statement that also reads the
field, and the gate's "would add compile errors".

## `batch` (built)

- `steps`: a list of `{tool, args, id?}`, each a tool and the arguments it takes
  alone; `check`: `"each"` (default) or `"end"`. A tool is named as the server
  names it (`rename`) or as a client does (`mcp__<server>__rename`, any server
  name; roadmap #100).
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
- Kept in memory per server process (`docs/design.md`, question 3: not on disk).
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
- Overloads and generics have their own refusals in each tool; checked exceptions
  are reported through `preserve_exceptions`. A shared wording for these across
  tools is not written yet.
