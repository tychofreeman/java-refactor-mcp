# Generics: a sketch

Status: a sketch for choosing the shape, not a decision to build. Written 2026-11-13 against
`main` at 63c9cde, from reading the docs and the source; nothing was built or run, so every
"works today" below is read from the code, not seen. Where I am unsure it says so.

## What and why

A review found generics are a gap: the tools rename a type parameter (`Rename.renameTypeParameter`,
roadmap #84) and carry type arguments that are already written, but no tool introduces a type
parameter, `extract_supertype` refuses a generic class, and nothing writes a bound or a wildcard
on purpose. Three slices: (1) introduce a type parameter, on a class or a method; (2)
`extract_supertype` from a generic class; (3) bounds and wildcards.

The evidence is thin, and the owner should see that:

- **The friction log has no entry asking for any of the three.** Its generics entries are about
  other things and are done: an overload named with `Map<String, Integer>` (2026-11-03, #36), a
  stub replaced by erasure (#107), a stub typed `List<T>` from an enhanced for (#68, #69).
- Slice 2 has one written trace: "Generic classes" is the first follow-up in
  `docs/sessions/extract-supertype.md`, and the refusal there today names no fix
  ("Repo is generic, which extract_supertype does not handle yet."), against CLAUDE.md's rule
  for refusals. No golden pins that refusal.
- Slices 1 and 3 rest on the review and on the owner asking for a sketch. CLAUDE.md says not to
  add tools speculatively, so the shapes recommended below add **no tool**: two options on
  existing tools, one lifted refusal, one shared check.
- Part of slice 3 is already there (see "What already works").

## What already works (read, not run)

- `change_type` writes `type` as text, so `type: "List<? extends Item>"` on a parameter, a return
  type or a field is accepted today; the family's declarations change, calls are not edited, and
  the gate refuses a body or a call it breaks (`Signatures.changeType`, `ChangeType.run`). A
  field's type can also become `T` once the class declares `T`. What is missing is a behavior
  check, not the edit.
- `add_supertype` takes the supertype as written (`Finder<K, V>`).
- `extract_supertype` from a **non-generic class with a generic method** should already work:
  `ExtractSupertype.signature` copies the text after the modifiers, which includes `<T>`, and
  `PullUp` moves a member's text whole. Only the class's own type parameters are refused.
- `NameCheck` skips type variables ("a type variable belongs to its class: the gate judges"), so
  a pulled-up member that names `T` is not refused by it.
- `extract_function` copies the enclosing method's type parameters as written
  (`Extraction.typeParameters`); `convert_to_record` carries the class's. Both are the model for
  "copy the declaration's text, bounds included".
- A bound can be typed today with `replace_text` on `<T>`: gated, but an edit, with no check that
  overrides and overloads still mean the same.

## Examples

Calls are written as JSON; `file` paths are under a source root.

### Slice 1: introduce a type parameter

**1. A class: a field, its constructor and its getter** (uses in three other files, one a test)

```json
{"tool": "add_parameter", "file": "com/example/Box.java", "name": "Box",
 "type_parameter": "T", "value": "String", "members": ["value", "Box(String)", "get"]}
```

```java
// Box.java, before                           // after
public class Box {                             public class Box<T> {
  private final String value;                    private final T value;
  public Box(String value) {                     public Box(T value) {
    this.value = value;                            this.value = value;
  }                                              }
  public String get() { return value; }          public T get() { return value; }
  public Box copy() { return new Box(value); }   public Box<T> copy() { return new Box<>(value); }
}                                              }

// Use.java, before                           // after
Box b = new Box("a");                          Box<String> b = new Box<>("a");
String s = b.get();                            String s = b.get();
List<Box> all = new ArrayList<>();             List<Box<String>> all = new ArrayList<>();
int n = new Box("abc").get().length();         int n = new Box<String>("abc").get().length();

// Named.java, before                         // after
class Named extends Box {                      class Named extends Box<String> {
  Named() { super("n"); }                        Named() { super("n"); }
  @Override public String get() { ... }          @Override public String get() { ... }
}                                              }

// src/test/java/.../BoxTest.java, before     // after
assertEquals("a", new Box("a").get());         assertEquals("a", new Box<String>("a").get());
```

Result: `added the type parameter T to Box; String became T in value, Box(String) and get();
7 uses pass String in 4 files (...)`. Every expression outside `Box` has the type it had.
`copy()` was not named: inside the class's instance code a use of `Box` becomes `Box<T>`
(question 3).

**2. Only the parameter** (no `members`): `class Registry` becomes `class Registry<T>`, every
use `Registry<String>`, nothing is typed `T` yet. This is `add_parameter`'s own rule ("the body
does not use it yet, so behavior is unchanged; add it, then change the body"); the members follow
by `change_type` with `type: "T"`, in the same batch with check `"end"`.

**3. A method**

```json
{"tool": "add_parameter", "file": "com/example/Lists.java", "name": "Lists.first",
 "type_parameter": "T", "value": "String"}
```

```java
static String first(List<String> xs) { return xs.get(0); }     // before
static <T> T first(List<T> xs) { return xs.get(0); }           // after
String s = Lists.first(names);                                 // a call: unchanged, T is inferred
```

Result: `added the type parameter T to Lists.first(List<String>); 3 calls infer String, none
changed`. For a method, every `String` in its parameter and return types becomes `T`.

**4. With a bound, because the body needs one**

```json
{"tool": "add_parameter", "file": "com/example/Range.java", "name": "Range",
 "type_parameter": "T extends Comparable<T>", "value": "Integer",
 "members": ["lo", "hi", "Range(Integer, Integer)", "contains"]}
```

```java
class Range<T extends Comparable<T>> {      // was: class Range, Integer where T is now
  private final T lo;
  private final T hi;
  Range(T lo, T hi) { this.lo = lo; this.hi = hi; }
  boolean contains(T x) { return lo.compareTo(x) <= 0 && x.compareTo(hi) <= 0; }
}
Range<Integer> r = new Range<>(1, 9);       // was: Range r = new Range(1, 9);
```

A declaration of two fields (`Integer lo, hi;`) is refused as `change_type` refuses it: "Split it
into one declaration per field first."

**5. Refused: the body needs the type**

`members: ["value", ...]` where `int size() { return value.length(); }`:

> adding the type parameter T to Box would add compile errors, so nothing was changed:
>   com/example/Box.java:9: The method length() is undefined for the type T
> A member's body uses value as a String. Give T a bound that has what the body calls
> (type_parameter "T extends CharSequence"), or leave value out of members.

**6. Refused, before anything is written**

- `value: "int"`: "int is a primitive, which cannot be a type argument. Change the members to
  Integer first (change_type, which checks the uses a boxed type changes), then pass value
  Integer."
- A static member in `members`: "Box.DEFAULT is static, and a static member cannot use T, the
  type parameter of its class. Leave it out of members; for a static method, add the type
  parameter to the method (name Box.of)."
- A method a lambda implements: "Parser.parse(String) is implemented by a lambda
  (com/example/Use.java, line 12), and a lambda cannot implement a generic method. Turn the
  lambda into an anonymous class first (convert_lambda with to anonymous)."

### Slice 2: extract_supertype from a generic class

**7. An interface that needs one of two parameters**

```json
{"tool": "extract_supertype", "file": "com/example/Repo.java", "name": "Repo",
 "supertype": "Finder", "kind": "interface", "members": ["find"]}
```

```java
// Repo.java, before
public class Repo<K extends Comparable<K>, V> {
  public V find(K key) { ... }
  public void save(K key, V value) { ... }
}
// after
public class Repo<K extends Comparable<K>, V> implements Finder<K, V> { ... }
// Finder.java, new
public interface Finder<K extends Comparable<K>, V> {
  V find(K key);
}
```

With `members: ["size"]`, where `public int size()` names neither: `public interface Sized`, and
`Repo<K, V> implements Sized`. With a method that names only `V`: `Finder<V>`.

**8. A bound that names another parameter brings it along**

`class Index<K, V extends List<K>>`, `members: ["values"]` where `public V values()`: the new
interface is `Listing<K, V extends List<K>>`, since `V`'s bound needs `K`; the class
`implements Listing<K, V>`.

**9. A superclass, the old superclass kept**

```json
{"tool": "extract_supertype", "file": "com/example/Repo.java", "name": "Repo",
 "supertype": "Cached", "kind": "class", "members": ["cache", "evict"]}
```

```java
// before
public class Repo<K, V> extends Store<V> {
  protected final Map<K, V> cache = new HashMap<>();
  public void evict(K key) { cache.remove(key); }
  public V load(K key) { ... }
}
// after
public class Repo<K, V> extends Cached<K, V> { public V load(K key) { ... } }
// Cached.java, new
public class Cached<K, V> extends Store<V> {
  protected final Map<K, V> cache = new HashMap<>();
  public void evict(K key) { cache.remove(key); }
}
```

The new class declares what its members use and what `Store<V>` uses.

**10. Refused**

- `supertype: "V"`: "V is a type parameter of Repo: in Repo's header the name would mean it, not
  the new interface. Choose another name."
- A bound the new file cannot name: "the bound of K uses the private type Repo.Key, which
  Finder, in its own file, cannot name. Make Repo.Key package-private or move it to its own file
  first (move_type)." (`ExtractType.unusable` has this rule and wording for `extract_type`.)

### Slice 3: bounds and wildcards

**11. A parameter that is only read becomes `? extends`** (accepted today; the check is new)

```json
{"tool": "change_type", "file": "com/example/Cart.java", "name": "Cart.addAll",
 "parameter": "items", "type": "List<? extends Item>"}
```

```java
void addAll(List<Item> items) { for (Item i : items) { lines.add(i); } }            // before
void addAll(List<? extends Item> items) { for (Item i : items) { lines.add(i); } }  // after
cart.addAll(books);   // a List<Item> still compiles; a List<Book> now would too
```

**12. A parameter that is only written becomes `? super`**

`void drainTo(List<Item> out) { out.addAll(lines); }` with `type: "List<? super Item>"`.

**13. Refused by the gate: the body writes**

`type: "List<? extends Item>"` where the body has `items.add(gift);`:

> changing the type of items of Cart.addAll(List<Item>) to List<? extends Item> would add compile
> errors, so nothing was changed:
>   com/example/Cart.java:14: The method add(capture#1-of ? extends Item) in the type
>   List<capture#1-of ? extends Item> is not applicable for the arguments (Item)
> The body adds to items, which List<? extends Item> does not allow. If it only adds, try
> List<? super Item>; if it reads and adds, keep List<Item>.

**14. Refused by the new check: a call would pick another overload**

`Cart` also has `void addAll(Collection<? extends Item> items)`, and `Use` calls
`cart.addAll(books)` with a `List<Book>`, which today can only mean that one:

> changing the type of items of Cart.addAll(List<Item>) to List<? extends Item> would make the
> call at line 8 of com/example/Use.java run Cart.addAll(List<? extends Item>), not
> Cart.addAll(Collection<? extends Item>) as it does now. Rename one of the two first.

**15. A bound added**

```json
{"tool": "change_type", "file": "com/example/Sorter.java", "name": "Sorter.max",
 "type_parameter": "T", "type": "Comparable<T>"}
```

```java
static <T> T max(List<T> xs, Comparator<T> c) { ... }                          // before
static <T extends Comparable<T>> T max(List<T> xs, Comparator<T> c) { ... }    // after
```

`name: "Sorter"` for the class's own `T`; `type: "Object"` takes the bound away. Uses are not
edited: a `Sorter<Thread>` or a call that infers a `T` outside the bound is the gate's refusal,
naming the file and line, "Change that use first, or choose a bound it meets."

**16. Refused: an override would stop overriding**

`class Box<T> { void put(T t) }`, and `class Legacy extends Box { void put(Object o) {...} }`
(a raw supertype, no `@Override`); the bound `Comparable<T>` on `Box`'s `T`:

> the bound Comparable<T> changes Box.put(T) from put(Object) to put(Comparable) once compiled,
> and Legacy.put(Object) (com/example/Legacy.java, line 5) would no longer override it. Give
> Legacy's extends its type argument first, or write the bound as "Object & Comparable<T>",
> which keeps put(Object).

## Shape options

### Slice 1: where "introduce a type parameter" lives

- **(a) `add_parameter` with `type_parameter` (Recommended).** `type_parameter` (as written,
  with its bound) in place of `parameter` and `type`; `value` is the type argument every use
  passes, as it is the expression every call passes; `before` and `imports` as they are;
  `members` new. It is the same sentence as the tool's own: a parameter is added and every use
  passes one value for it, so nothing changes. Precedent: a record's component went here
  (roadmap #129, "the user chose this over a tool").
  Costs: `name: "Box"` already means Box's constructor (#331), so `type_parameter` is what tells
  the two apart, a fourth meaning for `name` in this tool; `parameter` and `type` stop being
  required in the schema and are checked by the handler instead; the description, already the
  longest of the signature tools, grows by three sentences.
- **(b) A new tool, `add_type_parameter`.** Its own description, required arguments and refusal
  list; the name fits Go and TypeScript, which both have type parameters. Costs: one more tool
  (about 40 of `ToolsTest.MAX_TOOLS` 50; I did not count exactly), a new name in the shared
  contract, and it is the option CLAUDE.md asks to avoid when no friction entry asks for it.
  This is the fallback if (a)'s description tests badly.
- **(c) `change_type` with `type_parameter`**: a field's or parameter's type becomes `T`, and `T`
  is declared when the class lacks it. Costs: it breaks `change_type`'s rule that uses are not
  edited (every `Box` in the project becomes `Box<String>`); one member per call, so a field,
  its constructor and its getter are three calls that only compile together; who declares `T`,
  and in what order with a second parameter, is implicit.

With (a), `change_type` still plays its part: after example 2, `type: "T"` on a member is an
ordinary `change_type`.

### Slice 2: which type parameters the new type gets

No tool question: it stays `extract_supertype`, and the refusal goes.

- **(a) Exactly those the members use, found by binding, with those their bounds use, in the
  class's order, each declared as the class writes it (Recommended).** Costs: a walk of the
  members' types (signatures for an interface; whole members and the old superclass's type for
  a class).
- **(b) All of the class's, always.** Simplest and always compiles; `Sized<K, V>` for `int
  size()` is wrong enough that a model would have to fix it by hand, and no tool removes a type
  parameter.
- **(c) A `type_parameters` argument to choose them.** One more argument for something the
  bindings decide; a wrong list is one more refusal.

### Slice 3: bounds and wildcards

- **(a) `change_type` (Recommended).** Wildcards need no new argument: `type` already takes
  them; what is added is the check of example 14 and goldens that pin both. A bound is
  `type_parameter` with `type` as the bound, on a class (`name: "Sorter"`) or a method.
  Costs: `change_type` gets a third target after fields and methods; the model must write the
  wildcard itself (question 5).
- **(b) `change_signature` for a method's bound** (`type_parameter`, `bound`): it owns a
  method's contract (#137). Costs: a class's type parameter is no method's signature, so
  class bounds would live elsewhere, and wildcards would stay on `change_type`: three places.
- **(c) A new tool** (`generalize_parameter`: it reads the body and writes `? extends` or
  `? super`). Costs: a tool, and the body analysis repeats what the gate already decides.
- **(d) Nothing: `change_type` for wildcards as today, `replace_text` for bounds.** Free.
  Costs: examples 14 and 16 are applied, not refused.

## Semantics

### One check all three slices share: calls keep their targets

Erasure makes the hazards here compile. One new check covers most of them, named `CallTargets`
below: for the files a change is checked in, every method call, `new`, `this(...)`, `super(...)`
and method reference resolves, after the change, to the declaration it resolved to before. It
runs once the change is written and puts the files back when it refuses, as
`Rename.checkMeanings` does. Declarations are matched by where they stand (as `NameCheck` does
with `at(file, position)`), not by binding key: a method's key holds its signature, which these
changes alter. Unsure: how `Rename.checkMeanings` behaves inside a batch whose gate is deferred;
this check should do the same.

### Slice 1 at the declaration

- `<T>` after the type's name (or `, T` in its list; `before` places it), or before a method's
  return type. Javadoc is not written; the result says so when the declaration has `@param` tags.
- Class: in each of `members`, every type node in its declared types (a field's type, a
  method's or constructor's parameter and return types, `List<String>` included) that binds to
  `value`'s type becomes `T`. Bodies are not rewritten (question 2).
- Method: the same for its own signature, in every declaration of its family
  (`MethodFamily`), so overrides stay overrides.
- Refused up front, each naming the fix: a primitive `value`; an enum, an annotation or a
  `Throwable` ("cannot be generic"); a static member, a static nested type or a field with an
  array creation (`new String[8]`: "an array of T cannot be created; leave items out, or make it
  a List first") in `members`; a name the declaration already uses for a type (the rule and
  wording of `rename`'s `rename_type_parameter_refused_clash`); a method that overrides a
  library's (the signature tools' wording); a method lambdas implement (example 6); a method
  whose parameters do not use the type ("T would be in the return type only, so no call could
  infer it").

### Slice 1 at each kind of use (class-level)

| Use | Becomes |
|---|---|
| A declared type: field, local, parameter, return, type argument, `Box[]` | `Box<String>` |
| `extends Box`, `implements Source` | `extends Box<String>`; the subclass's overrides are untouched |
| `new Box(x)` where a declared type, a `return` or an assignment fixes the target | `new Box<>(x)` (question 4) |
| `new Box(x)` elsewhere (a receiver, `var`, an argument), and `new Box(x) { ... }` | `new Box<String>(x)` |
| `(Box) o` | `(Box<String>) o`: same check when run; the result notes the unchecked cast |
| `o instanceof Box`, `Box.class`, `Box::get`, `Box::new`, `new Box[n]`, imports, Javadoc links | unchanged (none can take an argument) |
| `o instanceof Box b` | unchanged; `b` is then raw, and a use that needs `String` from it is the gate's refusal ("cast it inside the branch first") |
| A use inside `Box`'s own instance code | `Box<T>`; in its static code, `Box<String>` (question 3) |
| A class that is already generic, `Pair<A>` gaining `B` | `Pair<Integer>` becomes `Pair<Integer, String>`; a raw `Pair` and a diamond stay |
| A string, a comment, `Class.forName("...Box")` | not seen |

Method-level: calls are not edited. After writing, each call of the family must have the
parameter and return types it had (`IMethodBinding` of the call); one that would infer another
type is refused first ("the call at line 12 of Use.java would infer T as Object, not String");
writing `Lists.<String>first(x)` there is a later step.

### What staying the same means under erasure

| Hazard | Slice | Who catches it |
|---|---|---|
| A body needs the concrete type (`value.length()`, `switch (value)`, `String s = value`) | 1 | the gate; the refusal suggests a bound |
| Two methods with one erasure (`put(String)`, `put(Object)` once `String` is `T`) | 1, 3 | the gate ("same erasure") |
| A use outside the bound; a call that no longer infers | 3 | the gate |
| A wildcard the body's reads or writes do not allow; passing `items` on as `List<T>` | 3 | the gate |
| A call picks another overload: `print(value)` with `print(String)` and `print(Object)`; a widened parameter (example 14); a raw receiver whose `get()` is now `Object` | 1, 3 | `CallTargets` |
| A method stops overriding (example 16), or starts to | 3 | own check: each changed method's family (`Signatures.family`) is the same set before and after |
| A type at a use changes without an error (`var x = id("a")`) | 1 (method) | own check: types of calls of the family |
| A cast `(T) o` that did nothing now checks for the bound; a `T...` call's array is a `Comparable[]`, not an `Object[]` | 3 | exceptional behavior: `Exceptional.change`, refused under `preserve_exceptions` |
| Bridge methods appear in subclasses (`Named.get()`); erased signatures change (`get()` returns `Object`) | 1, 3 | not kept, said in the contract: only reflection and stack traces see it |
| Reflection by name and type (`getMethod("put", String.class)`); a `Serializable` class with no `serialVersionUID`, whose computed one changes | 1, 3 | not seen. The result could note a `Serializable` without the field; I would not build a reflection search |

Slice 2 changes no erasure, because bounds are copied exactly: the interface's `V find(K)` and
the class's compile to the same signature, so no bridge appears. That is the reason "with their
bounds" is a rule and not a nicety. `kind=class` is `pull_up`'s behavior and refusals, unchanged.

### Slice 2 in detail

- Used parameters: every name in the extracted text that binds to a type variable whose
  declaring class is the class (a generic method's own `T` travels in its text). Interface: the
  signatures. Class: the whole members, and the old superclass's type as written. Then the
  closure over bounds, in the class's order.
- The new header: each `TypeParameter`'s source text, as `Extraction.typeParameters` copies a
  method's. The class's clause: the names only (`Finder<K, V>`), through `AddSupertype.run`,
  which already takes type arguments; for `kind=class`, the same text replaces the old
  superclass.
- `PullUp` finds the new superclass by erasure (`supertype`), and moves text whole; the names
  mean the same because the new class declares the same names.
- Unchanged limits: a top-level class (`Opened.topLevel` finds classes and interfaces only, so a
  record is refused as "not a top-level type"; that wording is wrong for a record and worth its
  own small fix), no interface as the source, constructors not passed on.

### Slice 3 in detail

- Wildcard: nothing new at the declaration (`Signatures.changeType`: the family, typed
  lambdas). The gate's fix line gains a sentence when `type` holds a wildcard (example 13).
  Then `CallTargets` over the files that name the method.
- Bound: one edit, the `TypeParameter`'s bound added, replaced or removed. A method's bound
  changes in each declaration of the family. Then the gate, the family check and `CallTargets`.
  An intersection (`Object & Comparable<T>`) is allowed as written and keeps the erasure.
- Refused up front: `type_parameter` with `parameter` or `return_type`; a type parameter the
  declaration does not have (its type parameters listed); a library override.

## How the type argument at each use is found

- Slice 1: it is not found, it is given: `value`, the same at every use, which is what makes the
  change behavior-preserving. `value` is resolved once in the declaring file (wrapped as
  `Signatures.parsesAs` wraps a type) and written at each use by `Extract.typeName(binding, at)`,
  so a nested type is named as that file can name it; `Imports.fix` adds the import per file,
  `imports` settles an ambiguous name.
- Uses: `Holding.of(project, "Box", file)`, every project file whose text holds the name, parsed
  together (`Jdt.parseAll`, one environment, so bindings compare); each `SimpleType` or
  `QualifiedType` whose binding's `getTypeDeclaration().getKey()` is the class's, as
  `Rename.refersTo` finds a type's uses. A file that never writes `Box` has no type node to
  change (`var b = factory.box()` follows `factory`'s declaration).
- Other files and tests: `SourceProject.javaFiles()` walks every source root, `src/test/java`
  included for a Maven project, so tests change in the same call and are in the gate.
- Diamond: decided by where the `new` stands, not by running inference: a variable's declared
  type (not `var`), an assignment to one, a `return`. After writing, one parse checks that every
  rewritten `new` has the type `Box<String>` (`resolveTypeBinding().getTypeArguments()`); one
  that does not is written out in full. Unsure: JDT's inferred type for a diamond in an argument
  position; that is why arguments get the full form.
- Slice 2: the arguments are the class's own parameter names. Slice 3: nothing is passed.

## Open questions for the owner

1. **Slice 1's home.** (a) `add_parameter` with `type_parameter` (recommended: no new tool, and
   the record precedent); (b) a new `add_type_parameter` (clearer schema, one more tool);
   (c) `change_type` (breaks "uses are not edited").
2. **What becomes `T` in a class.** (a) The members named in `members`, signatures only; a body
   that then does not compile is refused with the line (recommended: the model decides, the gate
   judges); (b) also the locals of those members' bodies whose initializer becomes `T`
   (`String v = value;`): fewer refusals, a small flow analysis; (c) one member named and the
   rest inferred through assignments, as Eclipse's "Infer Generic Type Arguments": one argument
   less, a constraint solver more.
3. **A use of the class inside itself** (`Box copy()`, `boolean same(Box other)`). (a) `Box<T>`
   in instance code, `Box<String>` in static code, the gate judging (recommended; right for
   copies, comparisons and withers, wrong for an instance method that builds a `Box` of a fixed
   string, which is refused with its line); (b) try `Box<T>`, and fall back to `Box<String>` for
   the uses the errors name: fewer refusals, a second gate round (about 2 s on a large project);
   (c) always `Box<String>`: never refused for this, but `copy()` then cannot be written and the
   class is generic in name only.
4. **Diamonds.** (a) `<>` where a declared type fixes the target, the full form elsewhere,
   checked by bindings (recommended); (b) always `new Box<String>(x)`: simplest and never wrong,
   noisier code that no tool tidies; (c) `<>` wherever inference gives the same type, found by
   writing it and checking: shortest code, the most reliance on JDT's inference.
5. **Who picks the wildcard.** (a) The model writes it in `type`, the gate and `CallTargets`
   judge (recommended: it is today's behavior plus a check); (b) `wildcard: "auto"` in place of
   `type`: the tool tries `? extends`, then `? super`, and refuses with both errors: two gate
   rounds, one argument; (c) a body analysis that decides without trying: most code, no evidence
   it is needed.
6. **A bound that changes what a cast checks** (the `(T) o` row). (a) Report it through
   `Exceptional.change` and refuse under `preserve_exceptions`, as the 2026-11-04 decision has
   it for exceptional behavior (recommended); (b) always refuse and suggest
   `Object & Comparable<T>`: stricter than the policy; (c) say nothing: against the policy.
7. **Order.** (a) Slice 2 first (recommended: the smallest, and the only one with a written
   follow-up and a refusal that names no fix), then the check and slice 3's wildcards, then
   slice 1; (b) slice 1 first, the one the review led with; (c) only slice 2 and the check now,
   the rest when the friction log asks.

## Increments, one PR each

1. **`extract_supertype`, `kind=interface`, from a generic class.** `ExtractSupertype` only (the
   precondition goes; used parameters; the header; the text passed to `AddSupertype`). Goldens:
   `extract_supertype_generic_interface` (example 7), `_generic_interface_subset` (one of two),
   `_generic_interface_none`, `_generic_bound` (example 8), `_generic_method` (pins what should
   already work), `extract_supertype_refused_type_parameter_name`, `_refused_private_bound`.
   No probe: nothing moves.
2. **`kind=class` from a generic class.** `ExtractSupertype`; `PullUp` read, probably not
   changed. Goldens: `extract_supertype_generic_class`, `_generic_class_superclass` (example 9).
   Probe: `extract_supertype_generic_class` (the program prints the same).
3. **`CallTargets`, and `change_type` with wildcards pinned.** A new class beside `NameCheck`;
   `Signatures.changeType` and `ChangeType.run` call it; the fix line for a wildcard. Goldens:
   `change_type_parameter_wildcard_extends`, `_wildcard_super`, `change_type_field_wildcard`,
   `change_type_wildcard_refused_writes` (the gate), `_refused_overload` (example 14). Probes:
   `change_type_wildcard_overload` (expect refused), `change_type_wildcard_read_only` (applied).
4. **`add_parameter` with `type_parameter`, a class, no `members`** (example 2): the walk over
   uses, the table above, diamonds, imports. `Tools.ADD_PARAMETER`, a new class (say
   `TypeParameters`) that `Tools` routes to before `ByLine.named`; `Holding`, `Imports`,
   `Extract.typeName` used. Goldens: `add_parameter_type_unused`, `_type_uses` (each row of the
   table), `_type_diamond`, `_type_subclass`, `_type_second_parameter`, `_type_refused_primitive`,
   `_type_refused_enum`, `_type_refused_name_taken`, and one with a test root if `GoldenTest`
   can hold two roots (I found no golden that does; otherwise a unit test). Probe:
   `add_parameter_type_subclass_override` (applied).
5. **`members`** (examples 1, 4, 5, 6): the declaration side, uses inside the class,
   `CallTargets`. Goldens: `add_parameter_type_members`, `_type_self_reference`, `_type_bound`,
   `_type_refused_body_needs_type`, `_type_refused_static_member`, `_type_refused_array_creation`.
   Probes: `add_parameter_type_overload_in_body` (`print(value)`, expect refused),
   `add_parameter_type_members` (applied).
6. **A method** (example 3): `MethodFamily` for the family and its calls. Goldens:
   `add_parameter_type_method`, `_type_method_family`, `_type_method_refused_lambda`,
   `_type_method_refused_return_only`, `_type_method_refused_inference`. Probe:
   `add_parameter_type_method_overload` (a call's result passed to an overloaded method).
7. **Bounds: `change_type` with `type_parameter`** (examples 15, 16). `Tools.CHANGE_TYPE`,
   `ChangeType` or `Signatures`, the family check, `Exceptional` for casts. Goldens:
   `change_type_bound_add`, `_bound_remove`, `_bound_method_family`, `_bound_intersection`,
   `change_type_bound_refused_use` (the gate), `_bound_refused_override`. Probes:
   `change_type_bound_raw_override` (expect refused), `change_type_bound_cast` (expect changed,
   reported).
8. **Records and interfaces as the target of 4 to 6**, and `<String>` written at a call that
   would infer otherwise, if 6's refusal is hit.

Each PR updates `docs/tool-contract.md` (the tool's section and its refusals; the "Open" line
about generics), the roadmap row in `docs/design.md`, and `Server.INSTRUCTIONS` only if a
shared rule changes. Before 5 and 7 are merged: `RefactoringStressTest` on Commons Lang, every
generic class and every `List<T>` parameter, counting applied and refused by reason; it sees
compile errors only, so the probes above carry the rest.

## Risks

- **Captures.** A wildcard's uses bind to captures (`capture#1-of ? extends Item`), whose keys
  differ per expression. The code already steps around them: `Extract.typeName` writes a capture
  as its erasure, `Extract.groundTypeName` and `LoopToStream` take `getWildcard()` and then the
  bound, `Stubs.typeName` likewise, `ExtractType.unusable` treats a capture as a type variable.
  `CallTargets` compares method declarations, never the types of expressions, so captures do
  not reach it; the method-level type check of slice 1 compares types and should compare
  `Extract.typeName` text, not keys.
- **Binding keys change with the signature.** `Box.get()`'s key differs once `Box` is generic,
  and a parameterized use's differs from the declaration's. The code's habit is
  `getTypeDeclaration().getKey()`, `getMethodDeclaration()` and `getErasure()` (`Rename`,
  `MoveType`, `SafeDelete`, `Signatures.family`); the new code must do the same, and match
  declarations by position where even that changes.
- **Recovered bindings.** A use in a file that already has errors may bind to a recovered type
  with no arguments. `NameCheck` skips them and `MethodFamily` refuses a method that "does not
  resolve; fix its compile errors first"; slice 1 should refuse the same way when a use of the
  class does not resolve, since a missed use is a raw type left behind, which compiles.
- **Raw types compile.** That is the main way slice 1 can be wrong silently: a missed `Box` is a
  warning, not an error, and the gate counts errors. A check after writing that no raw use of
  the class remains outside the unchanged rows of the table (`isRawType()`) closes it.
- **The gate's narrowed check.** A class's header changes, so every file naming it is checked
  again (`Headers`), which is what is wanted; a bound change reaches subclasses through the
  same rule. The narrowed rule once missed inheritance cases (`NarrowCheckStressTest`), so the
  goldens' comparison with a fresh count matters here.
- **Inference is JDT's, not javac's.** Where the two differ on a diamond or a generic call, the
  gate can pass what `mvn compile` fails. Writing the full form wherever the target is not
  declared keeps the tool off that edge; CI's own build is the backstop.
- **Out of this sketch**, the other generic refusals: `move_member` ("is generic. The receiver
  parameter is typed by the class's name..."), `subclass_to_strategy` ("has type parameters,
  which is not built yet"), `inline_function` ("is varargs or generic"), `extract_type` (a type
  variable). The contract's "Open" line asks for a shared wording for these; slice 2's reworded
  refusals could set it.
