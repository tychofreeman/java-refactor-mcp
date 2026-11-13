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

## What every tool shares

The server's MCP `instructions` (`Server.INSTRUCTIONS`, 2026-11-06) say once what descriptions
used to repeat: a change that would add compile errors is refused; a refusal changes nothing
and says why and what to send instead; edits declare stubs (a constructor stub sets the fields
its arguments each fit, 2026-11-07), write the constructor of final fields they leave unset in a
class without one (2026-11-07), and fix imports; lines are 1-based.
A description says what its tool does; the refusals each tool makes are listed in its section
here, not in its description (the user, 2026-11-06).

An edit (any tool through `Edits.gated`) that adds a final instance field with no value to a
class with no constructor but "not implemented" stubs also adds the constructor that sets every
such field, in declaration order, after the last of them; a stub of the same parameter types is
replaced in its place (2026-11-07, `FromFields.complete`). The result says so ("; added
constructor Reservation(String book, String member), since final fields book, member were
unset"), and, for a top-level class with no superclass, its instance fields final and one
constructor, adds "; Reservation now fits a record: convert_to_record makes it one".

A compile refusal lists the errors, then what to send. When the errors are blank final fields no
constructor sets ("The blank final field book may not have been initialized"), it names
`add_declaration` with `constructor: ["book", ...]`, sent with the fields in one batch
(`Gate.refusal`, 2026-11-06). When they are methods that must override in a type with no supertype
declaring them ("The method price() of type Cart must override or implement a supertype method"),
it names `add_supertype` for that type, before the methods in one batch (2026-11-06). Other errors
with these keep the edit's own advice after the hints.

## Names shared with gorefactor

Tools keep gorefactor's names where the Java tool does the same thing:
`inspect`, `rename`, the signature changes, `extract_variable`,
`inline_variable`, `inline_function`, `invert_if`, `remove_else`,
`move_statement`, `create_file`, `add_declaration`, `replace_declaration`,
`replace_text`, `batch` and `undo`, and (roadmap #120, 2026-11-06) `extract_function`, which
was `extract_method`. Aliases (`Tools.ALIASES`), accepted by `batch` and the CLI but not
published: `extract_method` for `extract_function`, and gorefactor's `method_to_function` for
`move_member` with `to` the method's own class (taken from `name`, `Type.method`). gorefactor's `replace_parameter` (roadmap #160, 2026-11-13) is `remove_parameter` with `value`, which it requires ("replace_parameter needs value."); the query form only, as the generators sketch decided. gorefactor's `create_loop`, `create_call` and `create_guard` (roadmap #166, 2026-11-13) are `insert_statements` with one `build` entry, unpublished: `file`, `line` and `where` place it, and the rest are the entry's keys (`over` is the loop's `loop`; `create_call` takes `call` or `new`, `args`, `into`, `type`; `create_guard` takes `guard` or `guard_null`, `exit`, `value`, `message`). Results and refusals are `insert_statements`'s; there is no `op` (the loop is a shell, as decided).

One `move` (roadmap #121, 2026-11-06, as gorefactor's): published in place of `move_type`,
`move_member` and `move_file`, which become aliases; it routes by its arguments. No `name`: the
file or directory (`to`, `copy`, `delete`). `name` with `package`: the file's top-level type to
that package. `name` (`Type.member`) with `to`: the method or static field to that class.
`name` alone (`Outer.Inner`): the nested type to its own file. `imports` (roadmap #131) goes to
the member route's import fix, as for `move_member`, and is refused on the others. Refused:
`package` without `name`, `copy` or `delete` with `name`, `to` and `package` together. Each route's own arguments,
results and refusals are those of its section below. Where Java still differs, the name says
so: `change_type` is for fields. `pull_up`, `push_down`, `add_supertype` and `encapsulate_field`
have no gorefactor counterpart (Go has no nested types or inheritance).

One `inline` (roadmap #122, 2026-11-06, as gorefactor's): published in place of
`inline_variable` and `inline_function`, which become aliases. With `line` (and `column`), the
local variable `name` declared or used there; without `line`, `name` as `Type.method`, every
call of the static method, which is deleted. A bare name without `line` is refused, naming both
forms. Each route's arguments, results and refusals are its section's below; Java has no
per-call inline yet. With `field` (roadmap #128, 2026-11-06, gorefactor's `inline_struct`),
`name` a class and `field` its field holding a project class nothing else uses: that class's
members take the field's place, `home.x` becomes `x` (`this.x` where a local hides it),
`home.m()` becomes `m()`, and the class's file is deleted (the inverse of `extract_type`
on a class). Refused: the class used elsewhere, a superclass or interfaces, a constructor with
work, an initializer other than `new Address()`, the field used other than to reach a member, a
name the holder has. gorefactor's edit
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

- no arguments (roadmap #111, 2026-11-06, as gorefactor's module overview): `Source roots, and
  in each its packages, ...`, each root (`. (the project root)` when it is the root), under it
  `  <package> (<n> lines): A.java 9, B.java 5`, then `<p> packages, <f> files, <l> lines`, why
  the classpath is empty when it is, and a line naming the other ways to read.

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
- `pattern` (2026-11-05; Java's own, not in gorefactor yet): a Java regular expression;
  with `file`, that file's matching lines, without it every Java file's, file by file,
  each as `<file>  N lines match /re/:` then numbered lines as above (the width of the
  largest number shown); at most 100 lines, then `... and N more; narrow the pattern, or
  give file.` No match is `no line of <file> matches /re/.` Refused: an invalid
  expression (with the reason), `pattern` with `start_line` or `end_line`. What a
  `grep -n` of Java found before; the read guard's refusal points to it.
- `names` (2026-11-05, gorefactor's argument), alone: declarations found anywhere in
  the project by name. `"Type"` (or `"Outer.Inner"`, a nested type) shows the type's
  lines with its bodies elided, as the file view does; `"Type.member"` shows each
  method of that name (every overload), a field or an enum constant in full, Javadoc
  included. Each under `<file>  lines a-b (name):` (`, bodies elided` for a type).
  Refused: no type of that name ("Name a type as Type or Outer.Inner"), a type with no
  such member ("inspect it by name to see what it declares"), and `names` with
  `file`, `pattern` or a line range.
- Not yet: gorefactor's no-argument overview, `function`, `targets` and `uses`.

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
  with the same parameter types, or member type, or whose file exists; a method
  name that the type or a supertype has for another method of as many parameters
  (an overload: a call of either could resolve to the other; probe
  `rename_into_overload`, 2026-11-05); a use that would mean another declaration after
  the rename, checked once it is written and put back when refused: a renamed use that a
  nested, enclosing or subclass declaration of the new name captures, or an existing use
  of the new name that the renamed declaration captures (`rename_refused_nested_capture`,
  2026-11-05; types too, `rename_type_captured_by_nested`, `rename_type_captures_existing`);
  a field
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
and Shop.price() to public`. A type of the default package gets a package line
and its file moves (#292); a file of the default package that names it gains
the import, and what it uses of the default package, which a package cannot
import, is refused by the type check, whose fix says to move those types in the
same batch with check `"end"`. Its imports of the new package are dropped.
Refusals: a name that is not a package name; a
file with several top-level types ("move the others to files of their own
first"); the package it is in; a target file that exists ("Rename one of the two types first").

## `pull_up` (built)

Arguments: `file` (the Java file that declares the class), `name` (`Sub.method`,
the class and the method, both as written in that file; the class is top-level),
and an optional `to` (the simple name of a direct supertype).

Moves a method or a field from `Sub` to its direct superclass, or to the direct
supertype named by `to`: the declaration, with its Javadoc, is removed from `Sub`
and added to the supertype where `add_declaration` would put it (a field after its fields,
a method at its end; 2026-11-07); imports are fixed in both files.
A name that is both a field and a method is refused; a field with other variables
in its declaration is refused (move one variable per declaration).
Calls need no change, since `Sub` inherits the method.
A method the supertype declares abstract with the same signature takes that declaration's
place (#320, 2026-11-10): its Javadoc stays, and `@Override` goes unless the abstract one had
it; the supertype's other subclasses keep their overrides. The supertype stays abstract; when
nothing else in it is, the result says it could be made a concrete class.
- Refused: no superclass and no `to`; `to` that is not a direct supertype; a
  supertype outside the project's sources; a method the supertype already
  declares (the same signature) other than abstractly; a method that overrides a concrete method the
  supertype inherits (`Root.name()` for `Base extends Root`: `Base` and its other
  subclasses would run the pulled body); a bare name that matches several overloads,
  which are listed (name one with its parameter types, `Sub.hello(int)`); a
  class that is not top-level.
- Type-checks the project: a method that uses what only `Sub` has is rolled
  back and refused, with the gate's "would add" wording.

## `extract_supertype` (built)

Roadmap #85, 2026-11-04. `extract_interface`, the old name in Go and TypeScript (renamed
there on 2026-11-06, gorefactor's #601), is an unpublished alias. Arguments: `file`, `name` (a top-level class), `supertype` (the new type's simple name), `kind` (`interface` or
`class`), `members` (names, with parameter types for an overload).

- `kind=interface`: `<Supertype>.java` in the class's package declares the
  signatures of the named methods (public instance methods only, with their
  `throws`), and the class gains `implements <Supertype>` (as `add_supertype`).
- From a generic class (roadmap #144 and #145, 2026-11-13), the new type declares exactly the
  class's type parameters its members name (an interface: the signatures; a class: the whole
  members and the old superclass's type), found by binding, and those
  their bounds name, in the class's order, each as the class writes it; the class passes their
  names. `Repo<K extends Comparable<K>, V>` with `V find(K key)` gives `interface Finder<K
  extends Comparable<K>, V>` and `implements Finder<K, V>`; with `V first()`, `Source<V>`; with
  `int size()`, `Sized`. Bounds are copied exactly so the interface's methods erase to what the
  class's do, and no bridge method appears. A generic method's own type parameters travel in
  its signature. `Repo<K, V> extends Store<V>` with `cache` and `evict` gives `class Cached<K,
  V> extends Store<V>` and `Repo<K, V> extends Cached<K, V>`; `pull_up` then moves each
  member's text whole, and the names mean the same because the new class declares them.
- `kind=class`: `<Supertype>.java` extends the class's old superclass, if any,
  and takes its place in the class's `extends`; each member moves into it as
  `pull_up` moves it (fields and methods), with `pull_up`'s refusals.
- One call: a refusal at any step changes nothing. Result: `extracted interface
  Pricing (com/example/Pricing.java) from Flat with price; Flat implements it`,
  or `extracted class Priced (...) from Flat with base and price; Flat extends it`.
- Refused: an interface, a file of the new name that exists, a member not found, a method that
  is not public or is static (interface); a `supertype` that is one of the class's type
  parameters ("V is a type parameter of Repo: in Repo's header the name would mean it, not the
  new interface. Choose another name."); a bound that names a private type ("the bound of K
  uses the private type Repo.Key, which Finder, in its own file, cannot name. Make Repo.Key
  package-private or move it to its own file first (move).").

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
  @Override public R m(P p) { ... } }`, from the lambda's resolved types; a wildcard in the
  target type takes its bound (`Consumer<? super Skipped>` gives `new Consumer<Skipped>()`,
  #344, 2026-11-11; it had been refused).
- Refused: `this`, or a bare call of a method of the interface, of its supertypes (an
  inherited default, roadmap #94) or of `Object`, in
  the code that moves (it would mean another object); an anonymous class with
  fields or other methods; a form that does not compile
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

## `extract_type` (built)

Roadmap #89, 2026-11-04. Named `extract_struct`, gorefactor's name, until 2026-11-06, when the
user renamed it: "struct" is Go's word, and the new type here is a class or a record; the name
is meant for all three servers. `extract_struct` stays an unpublished alias. Arguments (one shape since 2026-11-06, the user's
choice): `file`, `name`, `members`, `new_type` (`struct` until 2026-11-07, gorefactor's argument name; still accepted), `as`, `value_semantics`, `imports`. What is grouped
follows from `name`: a top-level class of `file` (its fields: extract class, below), a record
(its components, below), else a method (its parameters: a parameter object). `members` are
those, `as` the parameter, field or component that holds the new type. The names before
(`parameters` and `fields`, `parameter` and `field`) are still accepted (`Tools.ARG_ALIASES`),
not published; the text below uses them where it was written with them.

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
  Final fields without an initializer (#345, 2026-11-11) are set by each constructor
  that does not call `this(...)`: the new class gets a constructor taking them in
  order, the holder is declared without an initializer, and the first of a
  constructor's `this.f = e;` statements becomes `this.field = new <Struct>(e1, e2);`,
  the others removed. Each `e` must read only the constructor's parameters (never
  assigned) and constants, and call or create nothing.
  Refused: a field used outside its class ("street is used in Other.java line 5
  ... Encapsulate them first"), an initializer that reads anything but constants
  (new objects of constants are fine), a constructor that does not set such a field
  in a statement of its own body, sets it twice, sets it to more than parameters and
  constants ("the constructor at line 10 sets err to wrap(err), ..."), or reads one of
  the fields between setting them, a static field, a holder name the class already uses. Result: `moved street and
  city of Person into Address (com/example/Address.java), held in home`.
- On a record (`name` a top-level record, roadmap #118): the named components, next
  to each other, become one component `<Struct> <field>` of a new record in a file
  beside it (public when the record is); every construction passes `new
  Address("Main St", "Springfield")` in their place, every accessor call becomes
  `o.address().street()`, and a read of the field in the record's body
  `address.street()`. The record's generated `toString` changes, so the call takes
  `value_semantics=true` unless the record defines `toString` (the user's choice,
  2026-11-05, over doing it and reporting the change, and over writing a `toString`
  that prints the old text). Refused: components not next to each other, an explicit
  constructor (canonical, compact or other), a method reference to an accessor or
  `Order::new`, a record pattern of the record, a type the new file cannot name.
  Result: `moved street and city of Order into Address (com/example/Address.java),
  held in address; changed com/example/Order.java, ...`.

## `add_parameter` on a record

Roadmap #129, 2026-11-06 (gorefactor's `add_field`; the user chose this over a tool): with `name`
a record whose canonical constructor is implicit or compact, `add_parameter` adds a component
(at the end, or before `before`) and every `new Line(...)` and `this(...)` passes `value`.
Refused: a record that writes its canonical constructor out, a component of that name, a record
pattern of it. Result: `added the component int unitCents to Line; 1 construction passes 0`. For
a class, adding a field filled by its constructors stays a composition: `add_declaration` of the
field, `add_parameter` of the constructor, `insert_statements` of the assignment.

## `add_parameter` on an enum

Roadmap #150, 2026-11-13 (`docs/sessions/enums-sketch.md`, slice 2; the owner chose option A, an
option on `add_parameter`, over an `add_field` tool and over the composition): with `name` an
enum of `file` (`Color`, or `Shelf.Level` for a member enum), `add_parameter` adds a field with a
value per constant (`EnumField`):

- `private final <type> <parameter>;`, placed by kind;
- the constructor: written, taking the parameter, when the enum declares none; with one, the
  parameter is added (last, or before `before`) and `this.<parameter> = <parameter>;` goes last
  in its body;
- a record-style accessor `public <type> <parameter>()` (the sketch's question 4, option (a));
- each constant passes its entry of `values` (an object, constant name to a Java expression),
  else `value`: `RED` becomes `RED("#f00")`, `RED(1)` `RED(1, "#f00")`; a constant's body stays.

Result: `added the field String hex to Color, set by its constructor and read by hex(); 3
constants pass their values`. A plain parameter of the enum's constructor, with no field, is
still added by `line` or by naming the constructor with its parameter types (`Color()`,
`Color(String)`). Refused: a constant that neither `values` nor `value` gives a value ("values has
nothing for BLUE, and there is no value for the rest. Add BLUE to values, or pass value for every
constant values does not name."), a key of `values` that is no constant ("Color has no constant
PURPLE; its constants are RED, GREEN, BLUE."), two or more constructors ("Color has 2
constructors (Color(String), Color(String, int)), and each constant chooses one. ... name it:
Color(String)."), a field, constructor parameter or method without parameters of that name
("Color already has a field code."), a method of that name the enum inherits ("Color inherits
ordinal() from Enum; choose another name."), and `values` when `name` is not an enum ("values
gives each constant of an enum its own value, and Renamer is not an enum of ... Pass value, which
every call passes.").

## `add_parameter` with `type_parameter`

Roadmap #156, 2026-11-13 (the generics sketch, slice 1; the user chose this over a new
`add_type_parameter` tool and over `change_type`, which would break its rule that uses are not
edited): with `type_parameter` (as written, with its bound: `T`, `T extends Comparable<T>`) in
place of `parameter` and `type`, a class gets a type parameter, and `value` is the type
argument every use in the project passes, test roots included, so each expression outside the
class has the type it had. `name` is the class (a top-level or member class of `file`);
`before` names the type parameter to put it ahead of (default: last); `imports` as elsewhere.
`members` (roadmap #157) names the fields, methods and constructors of the class (`value`,
`Box(String)`, `get`; with parameter types for an overload) whose declared types become `T`
where they name `value`: a field's type, a method's or constructor's parameter and return types,
the type arguments in them included (`List<String>` becomes `List<T>`). Their bodies are not
changed: a body that then does not compile is the gate's refusal, whose fix line says to give `T`
a bound that has what the body uses, or to leave that member out. Without `members`, nothing is
typed `T` yet.

| Use | Becomes |
|---|---|
| A declared type: field, local, parameter, return, type argument, `Box[]`, a cast | `Box<String>` (the result names an unchecked cast) |
| `extends Box`, `implements Source` | `extends Box<String>`; the subclass's overrides are untouched |
| `new Box(x)` initializing or assigned to a variable declared `Box`, or returned from a method that returns `Box` | `new Box<>(x)` |
| `new Box(x)` elsewhere (a receiver, `var`, an argument, in a lambda), and `new Box(x) { ... }` | `new Box<String>(x)` |
| `o instanceof Box`, `o instanceof Box b`, `Box.class`, `Box::get`, `Box::new`, `new Box[n]`, imports, Javadoc | unchanged |
| A use in the class's own instance code (its header, its instance members, an inner class) | `Box<T>`; in its static code and static nested types, `Box<String>` |
| A class that is already generic, `Pair<A>` gaining `B` | `Pair<Integer>` becomes `Pair<Integer, String>`; a raw `Pair` and a diamond stay |

- Decided by the user, 2026-11-13 (each the sketch's recommendation): a use of the class inside
  itself is `Box<T>` in instance code and `Box<String>` in static code, the gate judging, over
  trying `Box<T>` and falling back per error, and over always `Box<String>`; a diamond where a
  declared type fixes the target and the full form elsewhere, over always the full form and
  over a diamond wherever inference agrees.
- `value` is resolved once, as a field of the class in its file would be, and written at each
  use by its simple names (a member type with its outer types, `Box.Entry`); each file's imports
  are fixed.
- After writing, one parse checks that each diamond of the class infers the argument its place
  asks for (one that does not is written out in full), that no use is left raw that was not
  raw before (refused: a missed use compiles with a warning only), and that every call runs the
  declaration it ran (`CallTargets`).
- Decided by the user, 2026-11-13 (the sketch's recommendation): `members` changes the named
  members' signatures only, the gate judging their bodies, over also retyping the locals their
  bodies initialize from them and over inferring the rest from one named member.
- A call whose target changes is refused by `CallTargets` (`print(value)` with `print(String)`
  and `print(Object)`), except a virtual call that now names a method its old target overrides
  (`value.equals(x)` runs `Object.equals` by name, `String.equals` at run time, as before).
- Result: `added the type parameter T to Box; String became T in value, Box(String) and get();
  24 uses pass String in 2 files (...); 3 uses in
  its own instance code pass T; a cast to it is now unchecked at line 21 of ...`; and `its
  Javadoc has no @param <T> yet` when the class's Javadoc has `@param` tags.
- Refused: `parameter` or `type` with it; a primitive `value` ("int is a primitive, which
  cannot be a type argument. Pass value Integer; ..."); an enum, an annotation, a Throwable;
  a record or an interface (not built yet); a name the class already uses for a type
  ("String is a type that Box already names (line 4); as its type parameter, String would
  hide it. Choose another name."), or already has as a type parameter; a `before` the class
  does not declare; a `value` that is a type parameter of the class; a use of the class's name
  that does not resolve. With `members`: a static member ("Box.DEFAULT is static, and a static
  member cannot use T, the type parameter of its class. Leave it out of members; a static
  method can take a type parameter of its own (add_parameter with type_parameter and name
  Box.DEFAULT)."); a field
  created as an array of `value` ("Box.items is created as new String[8], and an array of T
  cannot be created. Leave items out of members, or make it a List first."); a field declared
  with others in one statement ("Split it into one declaration per field first."); a method that
  overrides a supertype's ("Box.put overrides Base.put, whose signature keeps String: ...");
  a member whose declared types do not name `value` ("Box.size has no String in its declared
  types, so nothing in it would become T. Leave it out of members."); a nested type; a `value`
  that does not resolve in the class's file.
- `parameter` and `type` are no longer required by the schema, since `type_parameter` takes
  their place; without it the handler refuses a call that lacks them: "add_parameter needs
  parameter and type, ...; or type_parameter, to add a type parameter to a class."
- A method (roadmap #158): with `name` a method (`Lists.first`), the method and every
  declaration of its family (`MethodFamily`: those it overrides and that override it) declare
  the type parameter (`static <T> T first(List<T> xs)`; `before` among the method's own), and
  each `value` in their parameter and return types becomes it. Calls and method references are
  not edited: after writing, each must have the parameter and return types it had, inferring
  `value`, and run what it ran (`CallTargets`). Result: `added the type parameter T to
  Lists.first(List<String>); String became T in its parameter and return types; 2 calls and 1
  method reference infer String, none changed`. Refused: `members` with it; a constructor; a
  method of a record, an enum or an anonymous class (not built yet); a family a lambda implements
  ("... and a lambda cannot implement a generic method. Turn the lambda into an anonymous class
  first (convert_lambda with to anonymous)."); `value` in the return type only ("T would be in
  the return type only, so no call could infer it from its arguments. Name a method whose
  parameters use it."), or nowhere in the signature; a name the family already uses for a type
  or a type parameter; a call that would infer another type ("would change the types of the call
  at line 7 of com/example/Use.java: it would infer T as Object, not String. Give it an argument
  or a target typed with String first, or leave the method as it is."); a method that overrides
  a library's.
- Not built yet: records and interfaces as the class, and writing `Lists.<String>first(...)` at a
  call that would infer another type (issue #375).

## `type_switch_to_method` (built)

Roadmap #127, 2026-11-06 (gorefactor's `prepare_switch_for_types` and `type_switch_to_method`
as one tool, the user's choice). Arguments: `file`, `line` (the switch expression), `name`. A
switch over a value whose type `S` is a project interface or abstract class, each case a type
pattern giving an expression, becomes `s.name()`: `S` declares `R name();` (`public abstract`
in a class), and each case's type gets `@Override public R name()` returning its expression,
the pattern variable as the receiver (`c.r()` becomes `r()`, a bare `c` becomes `this`). Refused:
a switch statement, a `default`, a guard, a case that is not a type pattern giving an
expression, a type that is not the project's or already has `name()`, a case that uses a local,
a parameter, `this` or the method's class's members. Result: `declared double area() on Shape,
implemented in Circle, Square; the switch at line 5 of <file> is now s.area()`. Since
2026-11-06 also: a switch statement of arrow cases (an expression statement or a block each)
and an `if` chain of `s instanceof T v` make a void method of each case's statements, and a
`default` (or a chain's final `else`) becomes `S`'s own implementation (a `default` method of
an interface, a concrete one of an abstract class), the selector name as `this`; a body may
declare its own locals; a `return`, `break`, `continue` or `yield` leaving a case is refused.

### Over an enum: the field form

Roadmap #151, 2026-11-13 (`docs/sessions/enums-sketch.md`, slice 3, increment 5; the owner chose
the sketch's recommendations: slice 3 lives in `type_switch_to_method`, over a new
`enum_switch_to_method` tool and over a composition of `extract_function` and
`function_to_method`; the tool chooses a field when every case gives a compile-time constant (or
`null`), else a method per constant, with an optional `as`, over always a method and over `as`
required). A switch expression over an enum of the project (a top-level one), each case naming
constants and giving a constant expression or `null`, becomes `e.name()` (`name()` when the
selector is `this`), and the enum gets the field of the switch's type through `add_parameter`'s
enum edit (`EnumField`): `private final R name;`, the constructor written or widened, the
accessor `public R name()`, each constant passing its case's value as written (`case GREEN, BLUE
->` gives both), a `default` giving the value of every constant no case names. Result: `added the
field String label to Color, read by label(); 3 constants pass their case's value; the code at
line 5 of com/example/Paint.java is now c.label()`. Refused: `case null` ("case null cannot move
into Color: label() cannot be called on null. Test for null before the switch first, and take the
case out of it."); a case that uses a local or a parameter (the type-pattern wording, "Pass only
what the switch's value and constants give"); a static field named bare that the enum does not
declare ("the case of RED uses HEAVY, a static of Paint, which Color does not see by that name.
Qualify it first (Paint.HEAVY), with replace_expression."); a name in a value that the enum's
file would resolve to another declaration (`NameCheck`); a name the enum already has or inherits
(`add_parameter`'s wording).

### Over an enum: the method form

Roadmap #152, 2026-11-13 (the enum sketch, increment 6). When a case of a switch expression is not
a constant, for a switch statement of arrow cases (an expression statement or a block each), or
with `as` `method`: each constant a case names overrides `public R name()` with the case's body
(`return` its expression, or its statements), the switch's value as `this` (`c.name()` becomes
`name()`), in its own body, which is written when the constant has none (`RED {...}`) and
otherwise gets the method last. The enum declares the method: with a `default`, the default's body
as its own `public R name()`; without one, `public abstract R name();` when every constant has a
case, else (a statement only) `public void name() {}`. A case naming several constants is copied
into each (the owner's choice of 2026-11-13, the sketch's question 6 (a), over refusing a body of
more than one expression and over a private static method each calls), and the result says so.
`as` is `field` or `method` (optional, the sketch's question "what it writes" (a), over always a
method and over `as` required); `as` `field` refuses a case that is not a constant ("the case of
RED gives c.name().toLowerCase(), which is not a constant, so it cannot be a field's value. Leave
out as, or pass as method, for a method in each constant.") and a switch statement. Every moved
body passes `NameCheck` (a type the enum's file names otherwise is refused). Result: `declared
LocalDate next() on Schedule, overridden in DAILY, WEEKLY; the code at line 7 of
com/example/Plan.java is now s.next()`, with `; the case of GREEN, BLUE is copied into each of
them` for a shared case and `implemented` for a default. Refused besides the field form's: a
constant whose body already declares `name()` ("RED already declares label()."), a name the enum
has or inherits ("Color inherits name() from Enum; choose another name."), a bare static method of
another class (as a bare static field), a constant body whose closing brace shares a line with its
last member, and a block of a switch expression (`yield`, not built yet).

## Merging duplicate methods (a composition)

Roadmap #126, 2026-11-06: gorefactor's `merge_duplicate_function` is, here, a batch of `fold`
(the method to keep) then `inline` (the duplicate, now a call of it): the user chose the
composition over a `merge` option and a tool. Both methods static, each body one return or one
statement. Golden `batch_merge_duplicate_methods`.

## `add_result` (built)

Roadmap #124, 2026-11-06, gorefactor's name (the user chose a record result). Arguments: `file`,
`name` (`Type.method`), `type`, `result`. The method returns one more value that its callers
ignore: a value-returning method returns `<Method>Result(<old type> value, <type> <result>)`, a
record declared after it, each `return e;` becoming `return new TotalResult(e, 0);` (the type's
zero: 0, false or null) and each call whose value is used reading `.value()`; a void method
returns `type`, each `return;` and the end of the body giving the zero. Refused: an override or
overridden method, a method reference to it, a method that calls itself, a result named `value`,
a record name in use. Result: `added the result count (int) to Cart.total: it returns
TotalResult(value, count), each return giving 0; 1 call reads .value()`.

## `function_to_method` (built), and `move_member` in place

Roadmap #90, 2026-11-04 (gorefactor's `method_to_function`, `function_to_method`).

- Instance to static in place: `move_member` with `to` the method's own class; the
  method becomes `static`, takes the instance as `receiver`, and calls pass it
  (`Price.charge(this, a)`), as it does for another class. A field or method the instance
  has, its class's or a superclass's, is read through `receiver` (#330, 2026-11-10: an
  inherited one had been left bare, and moving to the superclass that declares it failed the
  type check).
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
project: the declaration, with its Javadoc, is removed from `Sub` and added to each
subclass where `add_declaration` would put it (a field after its fields, a method at its end;
2026-11-07); imports are fixed in every file touched.

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
  implicit one. When each argument fits exactly one field of T (declared in T, of the
  argument's exact type, not static, not final with a value; of several, the one a plain-name
  argument names), and no two fit one field, the constructor assigns them instead of throwing
  (2026-11-07): `Loan(String author, int days) { this.author = author; ... }`, labeled "Loan(String,
  int) setting author, days". It is real code, not a stub, so a test of it may pass at once, and
  `add_declaration` of the same constructor is refused as a duplicate; change it with
  `replace_declaration`;
- a static field read as `T.NAME` on a project class (roadmap #107, 2026-11-06): `static
  <type> NAME; // not implemented`, the type from the use as for a method's result, no value;
- a field read or written by a bare lowercase name nothing declares (2026-11-08): `<type> name; //
  not implemented` in the named type around the use, `static` in static code, typed as above, and
  as the other side of an assignment, or `int` for `++` and `--`; not for a call's receiver or a
  qualified name, nor for a field the edit removed. `add_declaration` of the real field replaces it;
- a class nothing declares (a type, the receiver of a static call
  `Tasks.run()`, or a capitalized qualifier `Limits.MAX`), in the using file's package under
  the first (main) source root, as `public class T {}`; a name that reads as a test double or
  helper (`Fake*`, `Stub*`, `Mock*`, `Dummy*`, `Spy*`, `*Test`, `*Tests`, `*Fixture`,
  `*Helper`, `*Fake`, `*Stub`, `*Mock`) under the root of the file that uses it (roadmap #113).
A call that is a lambda's body, or returned from one, gets the result type of
the method the lambda implements.
The body is `throw new UnsupportedOperationException("not implemented");`,
which `add_declaration` replaces. Members are public only when the type is in
another package. Not declared: methods of interfaces or library types, instance
fields, fields of interfaces, and a method or constructor the edit removed. The result ends
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
...Assertions.assertEquals`), before stubs are declared; in a file that imports from JUnit
Jupiter (`org.junit.jupiter`) or JUnit 4 (`org.junit`), also from its assertion class
(`Assertions`, `Assert`) when that class has a method the call resolves to. Static imports
added to a file with none go first, a blank line before the others. An import the running call
or batch added that a type declared in the file now shadows is removed by the next fix (a later
step declared the name it was found for; roadmap #108). When the file's earlier imports do not decide an ambiguous name, the candidate that the other
files of its directory import most is taken (strictly more than the others; `chose
org.eclipse.jdt.core.dom.Name over ...: 12 other files of this package import it`, 2026-11-06);
then the only one in a package the file imports from; then the only candidate in the project's
own sources is taken and named (`chose shop.order.Order over
org.junit.jupiter.api.Order: it is the project's`, roadmap #131); a refusal suggests the
project's class first, then `java.*`. Results end with
`; imported a.B, c.D, static e.F.g` and `; removed import e.F`, and their line numbers count the import lines. `create_file`
content is a whole file and keeps its own imports.

The server runs one call at a time: calls sent together in one turn run in the
order they arrive, so a call that needs an earlier one's result can follow it
in the same turn only if it is sent after it.

- `create_file`: `file`, one of `content`, `declaration` and `test_for`, `overwrite`. With
  `test_for` (`Cart.total`, `Cart.add(int)`, or `Cart` for an empty test class), the file is
  a JUnit test class (Jupiter, else JUnit 4, else refused) with a red test of the method:
  the receiver from its fewest-argument constructor with placeholder arguments, the call,
  its result in a local, and `fail("write the assertion")`; the result ends `; a red test
  of Cart.total(): write its assertion in place of fail`. With `JREFACTOR_SKELETON_ONLY=1`
  (an experiment, off by default), Java `content` whose types declare members is refused
  ("skeleton-only creation is on: ..."); headers, enum constants and record components
  pass, and so do `declaration` and `test_for`. Under the flag, also refused (2026-11-06), each
  naming the operation that writes it: `add_declaration` content with a constructor that only
  assigns its parameters to fields of their names (`constructor`), or an `@Override` that only
  throws "not implemented" or only calls the supertype's method with its parameters
  (`implement`); `replace_statements` content that is the replaced statements wrapped in one
  `try`, `if`, `for`, `while` or `synchronized` (`surround`); and (2026-11-07) `replace_declaration` of a
  type whose new members include such a constructor or override, naming the `add_declaration` to
  send after it in the same batch;  any edit that writes such a member through its own text,
  `replace_statements`, `replace_expression`, `insert_statements` and `replace_text` of a `.java`
  file (`Generated.checkChanged`: each method the edit adds, or changes and that was not already
  such a member), naming the `add_declaration` to send in its place, which replaces it
  (2026-11-07). A member left as it was, or one a tool wrote that another edit beside it leaves
  alone, is not checked. Bodies with logic are not refused. With `declaration` (a
  `.java` file under a source root only), the file is the package line and that type
  header with an empty body, plus a "not implemented" stub for each abstract method its
  supertypes leave unimplemented, named in the result (`; stubs for what its supertypes
  leave abstract: Priced.price()`); a declaration with a body, or naming another type
  than the file, is refused. An existing file is refused unless `overwrite`; with `declaration`,
  when the file's type lacks supertypes the declaration names (a stub class a test declared,
  say), the refusal names the `add_supertype` calls that add them and keep its members
  ("send add_supertype with name Cart, relation implements, supertype Priced", 2026-11-06). A `.java` file must declare the
  package its directory implies under a source root. Result: `created <file>
  (<n> lines)`.
- `move_file` (roadmap #81, 2026-11-04, gorefactor's name): `file` (a file or
  directory), `to`, `copy`, `delete` (instead of `to`). Text files that are not
  Java under a source root; each goes through the journal, so `undo` puts it
  back, and directories a move or delete empties are removed. A moved or copied
  file keeps its mode (`mvnw` stays executable), and `undo` gives a file it puts
  back the mode it had (2026-11-05). Result: `moved
  docs/a.md to notes/a.md`, `copied ...`, `deleted cases/one (2 files)` (a
  directory names its file count). Refused: a path that does not exist, a `to`
  that does or that is inside `file`, Java under a source root ("Use move_type
  (with package)"), a file that is not UTF-8 ("Move this one with git mv").
- Edit primitives (roadmap #91, 2026-11-04, gorefactor's names), each with
  optional `imports`, through the gate with stubs and imports as every edit:
  - `replace_expression`: `file`, `line`, `expression` (exact text), `column`,
    `new`. Result: `replaced 0.2 at line 5 of <file>`.
    `build` in place of `new` (roadmap #165, 2026-11-13): one object, `{"call":
    "discounts.apply", "args": ["price", "customer"]}` or `{"new": "ArrayList"}`, written as a
    `build` call or construction entry is (a diamond for a generic class named without type
    arguments, the arguments checked against the declared overloads), without `into` or
    `type`. Refused: `new` with `build`, or neither; a `loop`, `guard` or string ("replace_expression's
    build is one call or construction ... A loop is a statement: send it as build of
    insert_statements or replace_statements"); `into` ("takes no into: the code around it uses
    the value"); and the call entry's refusals (the wrong number of arguments).
  - `insert_statements`: `file`, `line` (a statement directly in a block),
    `where` (`before` or `after`, default after), `content`, re-indented to that
    statement. Result: `inserted 1 line after the statement at line 5 of <file>`.
  - `replace_statements`: `file`, `start_line`, `end_line` (whole statements of
    one block), `content` (empty deletes). Refused: an `end_line` that does not
    end a statement of that block, with the lines that do. Result: `replaced lines
    5-6 of <file> with 1 line` or `deleted lines 5-6 of <file>`. `surround` in place of
    `content` (roadmap #123, 2026-11-06): the statements go inside a block with that head
    (`try`, `if (cond)`, `for (...)`, `while (cond)`, `synchronized (lock)`), one
    indentation step in; a `try` catches the checked exceptions they throw and rethrows them
    (`catch (IOException e) { throw e; }`, multi-catch for several, a free name), or has an
    empty `finally` when they throw none. Another head is refused, naming the forms.
  - `build` in place of `content`, on `insert_statements` and `replace_statements` (roadmap
    #149, 2026-11-13; the generators, `docs/design.md`): a list whose entries are statements as
    typed (strings) or objects that name what to write, in order. `{"call": "pricing.priceOf",
    "args": ["order", "customer"], "into": "price"}` writes `Money price =
    pricing.priceOf(order, customer);`: `call` is the method with what it is called on, `args`
    the arguments in order (always given, none guessed), `into` a name (a variable that is in
    scope there, or that an earlier entry declared, is assigned; any other name is declared as
    a local, with its type written out, never `var`; `return` returns the result; without
    `into` the call is a statement), `type` the type to declare the local with when it is not
    the result's. `{"new": "ArrayList", "into": "open", "type": "List<Order>"}` writes
    `List<Order> open = new ArrayList<>();` (a generic class named without type arguments gets
    a diamond; without `type` the local is the class, `Receipt receipt = new Receipt(order);`).
    The result names what was inferred after the tool's usual line: `inserted 1 line after the
    statement at line 31 of <file>; built Money price; total (assigned); imported
    shop.money.Money`. A method nothing declares is stubbed as in any edit, its result type
    from `type`. Refused, each naming what to send: `content` with `build` ("takes content or
    build, not both"), and `build` with `surround`; a key the entry does not take ("a call
    entry takes call, args, into and type; arguments is none of these"); the wrong number of
    arguments, with each overload's parameters by name ("Pricing.priceOf takes (Order order,
    String customer); args has 1 of 2", for a constructor "new Receipt takes ..."; an explicit
    constructor only, so a class with none still gets its stub); `into` with a method that
    returns nothing; a new local whose call is not declared, or does not fit its arguments,
    and has no `type` ("Pricing.quote is not declared, so the type of its result is not known.
    Pass type ..."); a generic class constructed with no arguments and no `type`; `type` with
    an `into` that is a variable there already; a result type that cannot be written
    (anonymous, an intersection). A checked exception the method does not declare, and an
    `into` of another type, are the gate's compile refusals.
  - A `loop` entry (roadmap #162, 2026-11-13): `{"loop": "orders", "name": "o", "form":
    "for_each", "type": "Order", "body": [...]}`, all but `loop` optional. `loop` is what is
    walked: an array or an `Iterable` (`for (Order order : orders)`), a `Map` (`for
    (Map.Entry<String, List<Order>> entry : byCustomer.entrySet())`), or with `form` `index` a
    `List` (`i < orders.size()`), an array (`i < names.length`) or an `int` (`i < count`).
    The element's type is read from the code and written out (a wildcard as its bound); `type`
    gives it when it cannot be, and is refused with `form` `index`. The variable is `name`, or
    the walked name's singular (`orders` gives `order`, else `item`), `entry` for a map, `i`
    then `j` for an index, each made free of the locals, the fields and the build's own names
    in scope. `body` is a list of entries as `build` is, written one indentation step in,
    which may use the variable; an empty loop is written when it is left out. The result
    names each head: `built for (Order order : orders); Money price`. Refused: what cannot be
    walked ("total is a Money, which a loop cannot walk: loop takes an array, an Iterable, a
    Map, or (with form index) an int"); a raw `Iterable` without `type` ("raw is a List with no
    type argument, so its elements' type is not known. Pass type"); `form` `index` over what is
    not a `List` or array ("has no index: use for_each"); a `name` that is a local or parameter
    in scope ("total is already a local here (declared at line 15) ... leave name out and it
    is order") or a field the variable would hide; a `form` other than `for_each` and `index`
    (naming `convert_loop` for the others); an expression that does not resolve, unless `type`
    is given, and then the stub `Stubs` writes for a bare name.
  - `surround` takes a loop object too (roadmap #163, 2026-11-13): `{"loop": "orders"}` with
    `name`, `form` and `type` as a `loop` entry takes them, and no `body`: the statements from
    `start_line` to `end_line` are its body, one step in. The head is decided as a `loop`
    entry's, and a variable the tool names is also kept free of the names those statements
    declare (`order2` when they declare `order`). Result: `replaced lines 16-17 of <file> with
    4 lines; built for (Order order : orders)`. Refused as a `loop` entry is, and for a key
    other than those four ("surround's loop takes loop, name, form and type: its body is the
    statements from start_line to end_line. To write other statements, send build.").
  - A `guard` entry (roadmap #164, 2026-11-13): `{"guard": "quantity <= 0", "exit": "throw",
    "value": "IllegalArgumentException", "message": "\"quantity must be positive\""}` writes
    `if (quantity <= 0) { throw new IllegalArgumentException("quantity must be positive"); }`,
    the exit one step in. `guard` is the condition as typed; `guard_null` names an expression
    and writes `x == null` (in parentheses when it is not a name, call or access). `exit` is
    `return` (with `value` when the method or lambda returns one), `continue`, `break`, `throw`
    (`value` the exception's type, `message` its argument), or for `guard_null` `require`,
    which writes `Objects.requireNonNull(x)` or `Objects.requireNonNull(x, message)`, imported.
    No return value, exception or message is guessed. The result adds nothing after the usual
    line (`; imported java.util.Objects` for `require`). Refused: no or another `exit`;
    `value` with `continue`, `break` or `require`, and `message` with other than `throw` or
    `require`; `throw` without `value`; a condition that is not boolean ("quantity is an int,
    not a condition"); `guard_null` of a primitive ("quantity is an int, which is never null");
    `return` without a value the method needs ("Ledger.totalFor(String) returns Money, so
    return needs a value. Pass value: what to return when orders.isEmpty()"), or with one in a
    method that returns nothing; `continue` outside a loop, `break` outside a loop or switch
    ("The guard is not inside a loop there, so there is nothing to continue. The exits there
    are return and throw"); a checked exception the method neither declares nor catches
    around the guard ("Ledger.load(String) does not declare IOException. Send change_signature
    with add_throws IOException before this step in the same batch (its callers follow), or
    throw an unchecked exception").
  - `add_case`: `file`, `line` (a switch statement or expression), `content`;
    before `default`, else at the end, indented as the other cases.
  - `add_element`: `file`, `line` (an array initializer or an enum), `content`;
    after the last element (on a new line when the elements are one per line).
- `replace_text`: `file`, `old`, `new`, `all_occurrences`, `append`. `old` must occur
  once unless `all_occurrences`; refused with the lines it occurs on. Result:
  `replaced <n> occurrence(s) in <file>, line(s) ...`. Not found exactly, `old` is found
  with the spacing within lines ignored, each run of spaces and tabs standing for any other
  and line breaks kept (gorefactor's rule; the user, 2026-11-06, replacing #106's uniform
  shift), and must then occur once; `new`'s lines move by the difference between the
  file's indentation and `old`'s on the first line that has both, their own relative
  indentation kept (the user's choice over writing `new` as sent, as Go does): `...; old
  matched with the spacing within lines ignored, and new was moved 2 spaces shallower to the
  file's indentation`. An `old` that starts with spaces and is found only inside a line's
  indentation (8 spaces inside 10) is not an exact match, so the loose match takes the whole
  indentation and `new` moves with it (roadmap #119). Not found at all, the
  refusal names where a line of `old` is, spacing ignored (`; its line int b() { is at line
  8`), as gorefactor's does. `append=true` in place of `old`
  adds `new` at the end of the file, after a line break when the file does not end in
  one (gorefactor's `append`, 2026-11-05): `appended <n> line(s) to <file>, line(s) ...`;
  refused with `old` or `all_occurrences`, and neither `old` nor `append` is refused.
  gorefactor's `files` and `function` are not built.
- `add_declaration` with `test_for` (`Cart.total`) in place of `content`: the member is a
  red test of that method, as `create_file`'s, named after it (`total2` when taken), with
  the framework's imports added.
- `add_declaration`: `file`, `content` (members), `type` (`Outer` or
  `Outer.Inner`; default the file's only top-level type). Members are re-indented to match and
  placed by kind (the user, 2026-11-07): a field or initializer after the type's last field, a
  constructor after its last constructor or field, either before its first member when it has
  none of those, and any other member at its end; members of one kind keep their order in
  `content`, and each place is a part of the result. The first member of an enum that has
  constants only goes after them, a `;` written after the last constant (on its own line after a
  trailing comma, or when there is no constant), and a body on one line is opened up (roadmap
  #141; every tool that adds a member to a type does the same). A field an edit adds that an earlier
  field's initializer reads (in the same call, or by a batch's end) moves to just before that
  field (#357, `FieldOrder`), so it is never read before it is defined. `after` and `before` (a member name, until
  then) are accepted and ignored (`Tools.RETIRED_ARGS`). A method with the same name
  and parameter types, compared erased (`List` and `List<?>`, roadmap #107), whose body is
  only `throw new UnsupportedOperationException("not implemented")` is replaced, and so is a
  field of the same name with no value whose line ends in `// not implemented`. A constructor
  (by `content` or `constructor`) also replaces one of the same parameter types whose body only
  assigns each parameter to the field of its name, as an edit (#272) or a stub (#273) writes it
  (2026-11-07): `replaced the constructor Reservation(String, String), which only set fields,
  ...`. Result: `added <members> to <type> in <file>, lines a-b` or `replaced the stub <m> ...`.
  `implement` in place of `content` (roadmap #123, 2026-11-06): `*` adds an `@Override` "not
  implemented" stub for each abstract method the supertypes leave (`Skeleton.unimplemented`);
  a method's name (`describe`, `Base.describe`, `describe(int)`), from the supertypes nearest
  first, `Object`'s included, adds that one: a stub if abstract, else an override whose body
  calls it (`return super.describe(n);`, `I.super.m()` for a default method). Refused: `*` with
  nothing left, a name no supertype declares (listing what it can take), several overloads.
  Private, static and final methods are not offered.
  `constructor` (field names, in order) in place of `content`: a constructor with a parameter
  per field, of its type as written, assigning each (`this.owner = owner;`), with the class's
  access. Refused: a name that is not a field (listing them), a static field, a final field
  with a value, a type that is not a class; for an enum the refusal names what to send ("Color
  is an enum; ... For a field with a value per constant, use add_parameter with name Color and
  values (...); for a constructor alone, add it as content (Color() {}).", roadmap #143, #150).
- `replace_declaration`: `file`, `name` (`Type`, `Type.member`, `member`; with
  `(parameter types)` for an overload), `content` (empty deletes, with the
  Javadoc; deleting a file's only top-level type deletes the file, "deleted Old
  and its file com/example/Old.java (was lines 5-8)", and a type still used is
  refused rather than declared again as a stub). A deletion is a safe delete
  (roadmap #83, 2026-11-04): refused while anything else uses it, "Price.rate()
  is used in com/example/Shop.java line 5. Remove or change those uses first,
  then delete it." (constructors by their calls too), and for a method that
  overrides a concrete one ("Sub.name() overrides Base.name(): deleted, its
  callers would run Base's instead ... replace its body with return super.name(); and
  delete it again."), unless its body only calls that method through `super` with its
  parameters in order, or is the same as that method's (#322, 2026-11-10: names compared by
  binding in one parse, a parameter by position, a method called on this by override; not when
  only the override is synchronized). Then its calls are not checked: they run the inherited
  method, which does the same. The old Javadoc stays unless content has its own. Refused: several
  members of that name (with their signatures), no such member (with the
  members there), a field declared with others in one statement. gorefactor's
  `line`/`column` addressing and `imports` are not built.

Refusals come back as an error result whose text starts `refused: `. Other
failures start `engine error (not a refusal; ...)`.

## Signature changes (built)

`add_parameter`, `remove_parameter`, `move_parameter` (roadmap #22). Each takes
`file` (the declaring file) and `name` (as `replace_declaration`: `Type.member`,
`member`, `m(int, String)` for an overload; a constructor by its type's name, `Renamer`,
also for a class (#331, 2026-11-10: it found the class, "Renamer is not a method or
constructor"; a class with several constructors lists them, one with none is refused),
and `parameter`. In place of `name` (2026-11-08, roadmap #130, the user's choice over the
declaration's line only), `line` of `file` finds the method from its declaration on that line,
else from the one project method the line's calls call (`m(...)`, `super.m(...)`, `new T(...)`,
`this(...)`, `super(...)`), declared in any file; `column` keeps the call starting there. A line
calling two methods is refused with each one's columns ("line 6 of com/example/Use.java calls 2
methods: Pricing.price(int) at columns 14 and 27; Use.size(Pricing) at column 38. Pass column, or
name."), and so is a line with neither. `change_type` with `parameter` or `return_type` takes it
too (`ByLine.named`).
- `add_parameter`: `type`, `value` (a Java expression each call passes),
  optional `before` (default last), optional `imports`; `values` (constant to expression) with
  `name` an enum, where `value` is then the rest's and optional when `values` names every
  constant (see "`add_parameter` on an enum").
- `remove_parameter`: also deletes the parameter's `@param` tag. Optional `value` (#315,
  2026-11-11, the user's choice over a `parameter_to_field` tool): a field (`out`, `this.out`,
  `Config.OUT`) or a literal that every call passes; the body may then read the parameter, and
  each read becomes the field (by its name, or `this.out` / `Config.OUT` when the body declares
  that name) or the literal. An instance field must be passed unqualified or through `this`, on
  a call on `this`. Refused: a method something overrides or implements, a call passing
  anything else ("the call at ... passes System.err, not this.out ..."), a parameter the body
  assigns, an instance field for a static method.
  `value` may also be an expression of the method's other parameters, fields, constants and
  literals, with calls (roadmap #147 and #148, 2026-11-13: the query form of `replace_parameter`,
  `docs/sessions/generators-sketch.md`): `width * height`, `order.customer`, `(hours - FULL) *
  rate`. Every call must pass it with its own arguments in place of the parameters (`fits(w, h +
  1, w * (h + 1))`), matched by structure, parentheses aside, each other name by binding; the
  body then starts with a local of the parameter's name and type (`int area = width * height;`,
  after a `this(...)` or `super(...)`), which the body may assign, and a body written on one line
  is opened up for it. The result ends "; its body starts with int area = width * height". A
  value that may throw (a division, a dereference) threw among the call's arguments and now
  throws in the method: reported as a change to exceptional behavior, refused under
  `preserve_exceptions`. Refused: a call that passes another expression ("the call at ...
  passes w + h, not width * height (with that call's arguments: w * h), so area cannot become
  width * height. Change that call first, or keep the parameter."); an argument the value
  reads that has effects ("... passes next() for width, and width * height would then be
  computed from one next() where the call ran two. Put next() in a local first."), or that is
  a primitive of another type than its parameter ("... passes w (int) for width (long): the
  call computed w * h in that type ..."); an argument with effects between the arguments the
  value reads and the removed one, or, when the value reads a field that is not a constant,
  after them ("... passes raise() after n * factor; in the method, amount * factor would be
  computed after raise(), not before it. Put raise() in a local first, or move the parameter
  last (move_parameter)."); a name that is not another parameter, a field or a constant in the
  method ("value may use Box.fits's other parameters (width, height), fields, constants and
  literals; scale is none of these there."); a value that assigns or holds a lambda; a
  constructor that passes the parameter to `this(...)` or `super(...)`, or whose value reads a
  field that is not a constant; a method with no body; a varargs parameter.
  A call in the value (`order.customer()`, `label(order)`, `new Key(a, b)`) matches a call of the
  same method at the call site, or of one that overrides it when it is called on a receiver; a
  method of the method's own object matches only unqualified or on `this`, on a call on `this`.
  It moves from the call's arguments to the first statement of the body, so, further refused:
  an argument written after it that is not fixed (locals, parameters, constants, literals: "the
  call at ... passes mark() after order.customer(); in the method, order.customer() would run
  after mark(), not before it. Put mark() in a local first, or move the parameter last
  (move_parameter)."); an argument the value reads that is not fixed and that the value reads
  after one of its calls ("... passes cur for a, which a call in bump() + a could change: the
  call read it again as it computed bump() + cur ..."); a constructor ("... would run after
  the constructor above this one and the field initializers, not before them"). Every such
  removal reports the change to exceptional behavior, as a call may throw.
- `move_parameter`: optional `before` (default last).

The change covers the method's family: every project declaration that overrides
it or that it overrides, transitively, joined by binding key across files (an
interface method and all its implementations). Calls: method invocations
(`super.m` too), `new T(...)`, `this(...)`, `super(...)`, enum constants. A construction with
a class body (`new T(...) { ... }`, an enum constant with a body) is a call of the superclass's
constructor its arguments go to (`Signatures.constructed`, roadmap #142). Each
changed file has its imports fixed; the whole change passes the gate once. A type's name with
no constructor declared is refused, naming what adds one: "Renamer declares no constructor. Add
one with add_declaration (constructor), then change it."; for an enum, "(content: Color() {})",
since `constructor` writes a class's.

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

## `extract_function` (built; was `extract_method`)

gorefactor's `extract_function` (roadmap #23; the shared name since #120, 2026-11-06, with
`extract_method` an alias).

- Arguments: `file`, `start_line`, `end_line` (inclusive), `name`, optional
  `doc` (Javadoc text without `/** */`), optional `visibility` (`private`, the default,
  `package`, `protected`, `public`). Blank lines at either end of the range
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
- With `visibility` other than private (#321, 2026-11-10, the user): the method may
  override a supertype's method of its name and erased parameter types (not private, static
  or final), marked `@Override`, a template method's step. Refused then: a subclass that
  declares a method of that name ("FileRenamer already declares move, which would override
  the new method ..."), a supertype's method of that name the new one would not override, and
  a call of the overridden one that could then run the new one ("... the call at line 6 of
  com/example/Renamer.java would then run the new one for a FileRenamer: it is in
  Renamer.twice, which FileRenamer does not override"): every call must be on `this`, in a
  method of a supertype that the class overrides, not also called through `super`.
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
  expr;` after the type's fields (its first member when it has none, 2026-11-07), `field`
  adds `private final T name = expr;` there too
  (refused in a static member), and `parameter` adds a parameter to the method
  around it, as `add_parameter` does, every call passing the expression; the
  expression becomes the name. The expression must be literals and constants
  (static final fields): anything else would be evaluated at another time or
  place ("uses a local, a parameter, a field that is not a constant, or a
  call"). As a `parameter` (roadmap #125, 2026-11-06, gorefactor's `parameterize`), the
  expression may also use the method's parameters that it never assigns: each call passes it
  with its own arguments in their place, parenthesized unless a name or a literal
  (`total(n + 1, 3)` passes `(n + 1) / 10` for `qty / 10`); a call whose argument for such a
  parameter has effects is refused ("would run them twice"). Refused also: a field of that name
  already in the type. Result:
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

## `fold` (built)

Replaces code that does what an existing method does with calls of it (roadmap
#103, gorefactor's `fold`). Arguments: `file` (declares the method), `name`
(`Type.method`), `line` and `in` (optional: fold only the matches that start on that line
of `in`, or of `file`). The method stays.

- The body is the pattern: one `return e;` matches expressions; a void method's
  statements match the same run of statements in any block, and a void method of one
  expression statement also matches a lambda's expression body (`s -> err.println(s)` becomes
  `s -> skipped(s)`, #344, 2026-11-11). Parameters stand for any
  expression of their type (a reference type takes a subtype); a local the body declares
  stands for a local the code declares, which must not be used after the run; every other
  name must resolve to the same declaration, by binding key, so overloads match exactly.
- In a `String.format` of the body, a plain `%s` whose argument is a parameter matches
  constant text in the code's format, and the parameter is given that text as a literal:
  `"%s: %s", room, what` matches `"%s: temp", room`, and the call passes `"temp"`.
- A match is left, with the reason in the result, when the call would not reproduce it:
  two arguments, both not fixed and one with an effect, that the body uses in the other
  order than the parameters; a parameter used several times whose argument is not fixed
  and has an effect, or that the body could change between its uses; an argument the
  behavior gate refuses to move to the call (out of a branch or guard, past an effect); a
  private method and code in another top-level class; a type that does not fit; an
  argument of a subtype (or `null`) when the method's class or a supertype has another
  method of its name and arity, which the call could resolve to (`fold_left_overload`).
- An instance method (2026-11-08, roadmap #130, the user's choice of the class and its
  subclasses over the same class only and over any receiver): refused when a method of the
  project overrides it ("Cart.total is overridden by BigCart.total(), so a call of it could run
  that instead"). A match must be in an instance member directly in the method's class or a
  subclass, where `this` is the same object, and becomes the unqualified `total(...)`; one is left
  in an anonymous class, a nested or unrelated class, or static code, and in a subclass when the
  method is private or its body uses `super`. Overloads are looked for from the class the code is
  in. Goldens `fold_instance_method`, `fold_instance_refused_overridden`; probe
  `fold_instance_subclass` (an override of a method the body calls still runs).
- Result: `folded N matches of T.m into calls: <file> lines a, b; left K: <file> line L
  (reason), ...`. The call is unqualified in the method's own class, `T.m(...)` elsewhere,
  with an import when needed.
- Refuses: a method that is not static or has no body, varargs, a body that is not one
  return or a void method's statements (or returns early), a parameter the body does not
  use, a body that assigns a parameter or returns one, and no match a call can replace
  (with the reasons of those left).

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
  name; an argument the body does not use, that can stand as a statement (a call, an
  assignment, `++`/`--`, a `new`), is written as that statement (`next();`). The move to before the statement is checked by the gate as an extract; when
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
- A change of or to a primitive or boxed type (2026-11-05, the user's rule; `PrimitiveUses`)
  is refused at a use whose result the type decides: an operand of arithmetic, a shift,
  bitwise or string `+`, a unary operator other than `!`, `++`/`--`, a compound
  assignment's target, an argument of a call or `new`, a cast, a branch of `?:`, a
  switch selector, and for a boxed type `==`/`!=` or a call's receiver: "total is an
  operand of + at line 7 of F, where changing it from int to long could change the
  result". The uses are the field's in the project, the parameter's in the family's
  bodies, or each call's result. Copies, returns and ordering comparisons are allowed.
- Calls keep their targets (roadmap #146, 2026-11-13; `CallTargets`): after the change is
  written, every method call, `new`, `this(...)`, `super(...)` and method reference in the
  files that name the field or the method must run the declaration it ran before, or the
  change is refused and the files put back: "changing the type of items of
  Cart.addAll(List<Item>) to List<? extends Item> would make the call at line 9 of
  com/example/Use.java run Cart.addAll(List), not Cart.addAll(Collection) as it does now.
  Rename one of the two first." A widened parameter, a wildcard or a changed return type can
  make a call pick another overload and still compile. A call that does not resolve before or
  after (a batch whose check is deferred) is left to the gate.
- Wildcards (the same row): `type` takes them as written (`List<? extends Item>`, `List<? super
  Item>`); the model picks the wildcard, the gate and the check above judge it (the user,
  2026-11-13, over a `wildcard: "auto"` that tries both and over a body analysis). When the
  gate refuses a `type` with a wildcard, its fix line says what the wildcard does not allow and
  which to try: "List<? extends Item> can be read from but not added to, and cannot be passed
  on as List<Item>. If the code only adds to items, try List<? super Item>; if it reads and
  adds, keep List<Item>."
- Not built yet: locals, and fields declared with several names.

## `change_signature` (built)

Arguments: `file`, `name` (`Type.method`, with parameter types for an overload) or `line` and
`column` as `add_parameter` takes them, and at least one of `return_type` (the new type as
written, or `void`), `add_throws` and `remove_throws` (exception types, separated by commas);
optional `imports`, `preserve_exceptions`. The change is made in the method, in every override
and implementation (its family), and in each method of the project that a method reference
implements it with (`Renamer::moveFile` where a `Move` is wanted makes `moveFile` one more
declaration) (#342, the user's choices of 2026-11-12).

- `return_type`: each declaration's return type. For `void`, each `return e;` of those bodies
  and of the block lambdas that implement the method is rewritten: `return;` when `e` does nothing
  and cannot throw (names, literals, `this.x`, operators on them other than `/`, `%` and a
  string's `+` of an object), dropped when it ends the body on a line of its own; `e; return;`
  when `e` is a call, a `new`, an assignment or an increment, in braces where the return had none.
  An expression lambda that is such a statement stays; one without effects becomes `{}`. Another
  type is checked as `change_type`'s `return_type` is (the primitive and boxed rule).
- `add_throws`: appended to each declaration's `throws` that lacks it, imported.
- `remove_throws`: dropped from each `throws` clause that names it (by simple name, or by the
  qualified name given), the clause with it when it was alone.
- `callers`, with `add_throws` of a checked exception (the user, 2026-11-13; no body throws it
  yet, so each way keeps behavior):
  - `propagate` (the default): each method that calls one that now throws it, outside a try that
    catches it, declares it too, in its whole family, and its callers are followed in turn; a
    lambda's functional interface declares it when the project declares that interface. Where it
    cannot be declared, the call is caught: in a method named by `stop_at` (`Type.method`,
    several separated by commas), in an override of a library's method (`run()`), in a lambda of
    a library's interface, in an initializer block.
  - `catch`: every call that does not handle it is caught, and no caller's signature changes.
  - `refuse`: calls are left alone, and one that does not handle it is the gate's refusal.
  - A catch rethrows unchecked, `throw new UncheckedIOException(e)` for an `IOException` and
    `throw new IllegalStateException(e)` otherwise, the variable `e` or the first free `e2`,
    `e3`. A call in the try block or the resources of a try gets one more catch clause on that
    try, after its last one. Another gets a new try around its statement, through the end of its
    block when the statement declares a local that later statements use; an expression lambda
    becomes a block lambda around the try. Tries that would nest or overlap are written as one.
  - An unchecked exception (a `RuntimeException` or an `Error`) needs none of this.
- Calls are not edited otherwise. Result: `changed the signature of Move.apply(Rename) (return
  type void; throws IOException added; its callers now declare it: Service.put(String); caught
  and rethrown unchecked at <file> line 9) in 3 declarations (<files>)`, each list cut at eight.
Refusals: none of the three options; a type that does not parse; a constructor's return type, or
a `[]` written after the parameters; an override of a library's method; a `return e;` whose `e`
may do or throw something and is not a statement ("Put it in a local first"); a `remove_throws`
no declaration of the family names; a `callers` that is not one of the three, or a `stop_at`
without `add_throws` and `propagate`; an added exception that does not resolve ("Pass its
qualified name in imports"); a `stop_at` method no chain of calls reaches; a throwing method
used as a method reference where the exception cannot be declared ("Turn it into a lambda first
(convert_lambda)"); a call in a field's initializer or an enum constant, or in `this(...)` or
`super(...)`; a statement to wrap that shares its line with other code or holds a text block;
and the gate's, for a call that uses the result, a body that still throws a removed exception, or
a catch of one no longer thrown: "Calls are not edited ...: one that uses the result changes in
the same batch as this step, with check "end"."

## `add_supertype` (built)
Adds a supertype to a top-level class or interface, in its header. Arguments: `file`, `name`
(the top-level type), `relation` (`extends` or `implements`, the clause the supertype goes in)
and `supertype` (as written, such as `Base` or `List<String>`).

Result: `added S as a supertype of T (relation) in file`, with `; imported ...` when the edit
adds an import. Refusals: a relation other than `extends` or `implements`; a name that is not a
top-level type of the file that resolves (a record, an enum or an annotation type is named as
one, with the file's classes and interfaces: the wording of every tool that finds its type
through `Opened.topLevel`); `implements` on an interface; `extends` on a class that
already extends one; and the gate's "would add compile errors", naming them.

## `move_statement` (built)

Swaps a statement with the one before it (`direction=up`) or after it (`direction=down`) in the same
block. Arguments: `file`, `line` (the 1-based line the statement starts on), `direction` (`up` or
`down`), and `names_only` (optional flag; see below).

The two statements must be independent. By default (strict): they share no name that one of them
declares or writes and the other uses, and at most one has a side effect (a call, an assignment,
`++`/`--`, a `new`). With `names_only=true`, only the shared-name rule applies, so two calls may swap
order.

With `past_try=true` (#343, 2026-11-11, the user's option (a) over keeping the refusal or only
rewording it): when one of the two is a `try` and the other is not, the swap is made whatever
their side effects, without the behavior gate; the shared-name rule still holds. This changes
behavior on purpose, and the result says how: `...past the try at line 17 in <file>; it now
runs after everything the try does, and only when the try completes normally: not when it
returns, throws, breaks or continues (line 21 returns)`, or, moved above it, `it now runs
before everything the try does, and also when the try does not complete normally`. Refused
when neither is a try, or both are.

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

## `loop_to_stream` (built)

Arguments: `file`, `line` (the line the loop starts on), `preserve_exceptions`. An enhanced for
over an `Iterable` (`for (T x : source)`) becomes a stream running the same code for each element
in the same order. The body is read from the outside in: an if with no else that holds the rest
is `filter`; a first local computed from the element (its declared type the initializer's, not
primitive), when nothing after it reads the element, is `map`; the rest is `forEach`
(`source.forEach(...)` with no stage). A body that is one `out.add(e)` or `out.addAll(c)` of a
collection, or an if with an else whose every path ends in one of them or adds nothing (a
`flatMap` of `cond ? c.stream() : Stream.of(e)`, `Stream.empty()` for nothing; an array added
under an if is refused, as `Stream.of` would spread it), to a `List` or `Collection` declared `= new ArrayList<>()` just before the loop and
used nowhere else in it, is collected instead (`addAll` is `flatMap`): `toList()` when every later
use only reads the list and the stream's type is its element type, else
`collect(Collectors.toList())` (the user, 2026-11-10). A local declared with a literal just
before the loop that the loop only sets to one other literal (a flag) becomes
`v = source.stream().map(x -> { boolean vChanged = false; ...; return vChanged; }).reduce(false,
Boolean::logicalOr) ? set : initial` (no `? :` for `false` to `true`, a `!` for `true` to
`false`), `reduce` and not `anyMatch` since the loop runs every pass; a last filter whose body only
sets the flag maps to its condition. An `int` or `long` local the loop only adds to (one `v += e`
after the stages) is `mapToInt(x -> e).sum()` or `mapToLong`, and one `v++` or `v += 1` is
`count()`, cast to `int` for an `int`; a starting value other than 0 goes in front (`crate +
...`). A `float` or `double` sum is refused (roadmap #135). A flag set
then followed by `break;` under the last filter is a search: `anyMatch(x -> cond)`, or
`allMatch(x -> !cond)` for one that starts `true` and is set `false`; a break with no condition
above it is refused. A loop over `map.entrySet()` with no stages, whose entry is used only as
`e.getKey()` and `e.getValue()`, is `map.forEach((key, value) -> ...)`. A call that changes the
map a loop walks the entries, keys or values of (`put`, `remove`, `merge`, ...) is refused, as a
change to the source is. A `Map<K, List<V>> m = new HashMap<>()` declared just before a loop
whose body is one `m.computeIfAbsent(key, k -> new ArrayList<>()).add(value)` is
`collect(Collectors.groupingBy(x -> key))`, with `mapping` for a value other than the element,
when the map is only read afterwards; else `groupingBy(x -> key, HashMap::new,
Collectors.toCollection(ArrayList::new))`. A null key now throws in `groupingBy`: reported in the
exceptional part of the result, refused under `preserve_exceptions`. Comment lines between that declaration and the loop stay above
it. Stages go on lines of their own past 100 columns. Result: `replaced the loop at line N of <file> with a stream of <source>: filter, map,
toList()`. Refusals: no loop on the line; a loop that is not an enhanced for ("convert_loop with
to=for_each takes an index for or a for over an iterator"); a label; an array; a source that is not an `Iterable` with a type argument; a
loop variable of another type than the elements (`int` over `Integer`); a break, continue or
return that leaves the body; a local declared outside the body and assigned in it ("Sums, counts
and other results are not built yet"); a checked exception the body does not catch; a call that
changes the source (`names.add(...)` in a loop over `names`); a text block. The type check
catches a local read in the body that is not effectively final.

## `subclass_to_strategy` (built)

Arguments: `file` (the subclass's), `name` (the subclass), `method` (the one method it
overrides), `type` (optional), `preserve_exceptions`. A subclass that differs from its superclass
only by one overridden method becomes a function the superclass holds (#332). With `class
FileRenamer extends Renamer { @Override boolean move(String from, String to) {...} }`:

- `Renamer` gains `interface Move { boolean move(String from, String to); }` (named after the
  method, with its parameters and `throws`; public when the subclass is) and `private final Move
  move;` after its last instance field (`moveFunction` when a field has the name). With `type`
  (`BiPredicate<String, String>`), the field has that type, imported, and no interface is
  declared; its one abstract method is the one called.
- `Renamer.move` becomes `return move.move(from, to);`. Its old body becomes `private static
  boolean defaultMove(...)` and the override's `static boolean moveFile(...)` (the method's name
  and the subclass's name without the superclass's; public when the subclass is), both after it.
- Each constructor of `Renamer` takes the function last and assigns it first (after `super(...)`;
  one that calls `this(...)` passes it on), followed by a constructor with the old parameters,
  `this(log, Renamer::defaultMove)`, so existing constructions are untouched. A class with no
  constructor gets both.
- `new FileRenamer(log)` becomes `new Renamer(log, Renamer::moveFile)`: the arguments of the
  subclass constructor's `super(...)`, its parameters replaced by the call's arguments.
  `FileRenamer` as a type (a local, a field, a parameter, a return type, a type argument) becomes
  `Renamer`; an import of it is removed; its file is deleted.

Refusals: the subclass is not a top-level class extending a top-level class of the project in
another file, or either has type parameters; it declares more than its constructors and the
method; the superclass's method is abstract, or the method has type parameters; a class extends
the subclass; either body uses a member of the instance, `this` or `super` ("Pass what it needs
as a parameter first"); the superclass already declares a name the tool would write; a
constructor of the superclass takes varargs or has a variable named as the field; `type` is not a
functional interface of the method's arity; the subclass is used in an `instanceof`, a cast, a
class literal, a method reference or a catch, or named other than as a type; a construction has a
class body; a constructor of the subclass does more than call `super(...)`; a construction's
argument is not a name or a literal and the subclass's constructor does not pass each parameter on
once, in order.

## `convert_loop` (built)
Arguments: `file`, `line` (the line the loop starts on), `to`, `preserve_exceptions`. A loop
becomes another statement form running the same code in the same order (the user, 2026-11-10:
statement forms here, the functional ones in `loop_to_stream`). Built so far:

- `to=for_each` from an index for, `for (int i = 0; i < x.size(); i++)` over a `List` or `i <
  x.length` over an array (`++i` and `i += 1` too): each `x.get(i)` or `x[i]` becomes the element.
  A first local `T e = x.get(i);` the body does not assign names it and is dropped; else the name
  is the singular of `x` (`names` to `name`, `entries` to `entry`, a `List` suffix dropped) or
  `item`, numbered when taken. Refused when the body reads `i` otherwise, writes `x[i]`, calls a
  method that changes the list (`add`, `remove`, ...), or when `x` can be another list on a later
  pass: it must be a local or parameter the body does not assign, a final field, or a field the
  body does not assign and calls no method of the project ("Put it in a local before the loop
  first (extract_variable)").
- `to=for_each` from a for over an iterator, `for (Iterator<T> it = x.iterator(); it.hasNext(); )
  { T e = it.next(); ... }`: refused when the body uses `it` again (`it.remove()`).
- `to=iterator` from an enhanced for over an `Iterable` with a type argument: `for (Iterator<T> it
  = x.iterator(); it.hasNext(); ) { <the loop variable> = it.next(); ... }`, `Iterator` imported.
- `to=for` from an enhanced for over a `List` or an array: `for (int i = 0; i < x.size(); i++) {
  <the loop variable> = x.get(i); ... }`, under the same rules for `x` as above; the result notes
  a list that is not `RandomAccess`, whose `get` may walk it. A one-statement body goes in a new
  block.

A label stays on the loop; `break` and `continue` keep their meaning in every pair. Result:
`rewrote the loop at line N of <file> as a for (String name : names).` Refusals: no loop on the
line; a `to` not built; a for that is neither shape; a pair not built ("Convert it in two steps,
through for_each or while", "through for", "through while"); a loop already in the form.

- `to=while` from a do-while (roadmap #134, the user's choice of 2026-11-11): `do body while
  (c);` becomes `while (true) { body if (!c) break; }`, `c` negated as `invert_if` does (`!(c)`
  where that would compare a float or double), the `if` left out when the body ends in a return,
  throw, break or continue. A `continue` of the loop goes to the condition in a do-while and would
  skip it in the while: refused. A label stays on the while.
- `to=do_while` from a while: `if (c) { do body while (c); }`. The condition is then read twice on
  the first pass, so it may only read locals, with literals and operators; a call, a field or an
  increment in it is refused ("Put the condition in a local first").

- `to=while` from a for: `for (init; cond; update) body` becomes `init; while (cond) { body
  update; }`, each init and update a statement of its own, `while (true)` for a for with no
  condition (roadmap #133, 2026-11-11). The update also goes before each `continue` of the loop
  (unlabeled in no inner loop, or naming its label), in braces when the continue has none; it is
  not written last when the body ends in a return, throw, break or continue. The init and the
  while go in a block when a statement after the loop names a variable the init declares, or the
  loop is not a statement of a block; a label stays on the while.
- `to=for` from a while: the local declared just before it, which the condition reads and no
  statement after the loop names, becomes the init; the body's last statement, an assignment or
  an increment, becomes the update unless a `continue` of the loop would skip it (the result says
  it stays in the body). `for (; cond; update)` and `for (init; cond; )` when one is missing.

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

Moves a method of a top-level class to another top-level class, of the same file or (roadmap #109,
2026-11-06) another: `to` is the class's name, found as `<to>.java` in the project, or with its
package (`com.example.shop.Tax`) when several files declare one; refused naming them. Arguments:
`file`, `name` (`Type.method`, or `Type.method(params)` for an overload), `to` (the target class).
Imports follow in every file; a method moved to another package becomes public, and what its body
uses that the target cannot reach is the gate's compile error.

A static field (roadmap #109, 2026-11-06): `name` `Shop.MAX` moves its declaration (Javadoc
and all, widened from private, public across packages) to `to`, after its fields (before its
first member when it has none, 2026-11-07); `Shop.MAX`, a bare
`MAX` and `this.MAX` become `Config.MAX`, and a static import of it imports it from there (that
file's bare uses kept). Refused: an instance field (naming `extract_type` and `pull_up`), a
declaration of several fields, an initializer that reads anything but constants (it would run
when the new class is initialized). Result: `moved Shop.MAX to Config (com/example/Config.java);
2 uses now name Config`.

An instance method becomes static in the target and takes the source instance as its first parameter,
named `receiver`; every call in the project passes it (`Type.m(instance, args)`). A private method is
widened to package access (named in the result). Result: `moved Type.m to T in file; N calls rewritten
in K files`, with `; private widened to package access` when so.

Refusals: an override, a method another class overrides, a constructor, no body, a generic class, a
method reference to it, a call of itself (an instance method's; a static method's call of
itself moves with it, unqualified, #356), super, an anonymous or local class in the body, a use of
a private method or field of its class that stays behind, when the target is outside that class
(#317: "Main.renameAll uses Main.carryOut, private to Main, which Renamer cannot reach: move
Main.carryOut to Renamer first (a batch can hold both moves, that one before this)"; since
2026-11-10, the user's "yes, if it doesn't break anything", private methods that only the moved
one calls, directly or through each other, move first in the same call, each by its own move, and
the result names them; the refusal stays for one called from elsewhere or in a cycle), a call from
another class with no instance, a nested call inside an argument, and the gate's "would add compile
errors". A null instance that the method dereferences fails inside it, not at the call.

## `encapsulate_field` (built)

Makes a field private, with a getter and a setter, and rewrites the project's uses through them. Arguments:
`file`, `name` (`Type.field`), `getter` and `setter` (optional; default `getX` and `setX`).

A read becomes the getter call (`x` to `getX()`, `p.x` to `p.getX()`); a plain assignment statement
`x = v;` becomes `setX(v);`, and (2026-11-07, roadmap #130, as Go and TypeScript) a compound write
or `++`/`--` that is a statement of its own becomes the setter of the getter combined: `x += k + 1;`
to `setX(getX() + (k + 1));`, `p.x++;` to `p.setX(p.getX() + 1);`. The getter runs before the
right-hand side, as the field's value is saved before it in Java (probe
`encapsulate_field_compound_order`). A final field gets a getter only. Result: `encapsulated Type.field in file
with getX and setX; N uses rewritten in K files`.

Refusals: a name that is not a field, a declaration of several fields, a final field that is written, a
write inside an expression (`int was = x++;`), a compound write through a receiver that is not a
name or `this` (`next().x += 1`, which would run twice), a plain assignment that also reads the
field, and the gate's "would add compile errors".

## `batch` (built)

- `retry` (2026-11-06, the user's choice over keeping the passed steps and over a refusal
  only): after a refused batch, `retry: true` with `steps` of `{"step": N, ...}` (replaces
  step N; its `tool` kept when left out, changed when given), `{"after": N, ...}` (adds a step
  after step N, 0 for first) and `{"remove": N}` (drops step N; with an `after`, moves it, #318)
  runs the refused batch with those changes, its check kept unless given; numbers are
  the refused batch's, and `step` one past the last adds a new last step (2026-11-06). Nothing
  applies unless every step passes; a batch that passes forgets
  the refused one, and every refused batch ends by saying how to retry. Refused: a retry with no
  refused batch, a change with not exactly one of `step`, `after` and `remove`, a number outside
  it. The refusal's hint says a step's tool can change and how to move a step.
- `steps`: a list of `{tool, args, id?}`, each a tool and the arguments it takes
  alone; `check`: `"each"` (default) or `"end"`. A tool is named as the server
  names it (`rename`) or as a client does (`mcp__<server>__rename`, any server
  name; roadmap #100).
- Line arguments (`line`, `start_line`, `end_line`) are as read before the
  batch; the batch moves them past earlier steps' edits. A line an earlier step
  rewrote with a different line count is refused, naming the step; a line between
  the runs a step changed (below an import it added, above a method it added at
  the end) is only moved (#295).
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
  tools is not written yet. `extract_supertype` no longer refuses a generic class (#144,
  #145); `move` of a member, `subclass_to_strategy`, `inline` of a function and
  `extract_type` still refuse generics (`docs/sessions/generics-sketch.md`, "Risks").
