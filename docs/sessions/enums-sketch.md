# Enums: a sketch

Status: chosen. The owner took the Recommended option of every open question on 2026-11-13
(question 1 option (a): increments 1 to 3, then slices 2 and 3; slice 1 and increment 12 wait for
evidence). Increments are built one PR each; the roadmap in `docs/design.md` says which are done.
Written 2026-11-13 against `main` at 63c9cde. "Checked by a run" below means one call through
`Cli` on a copy of that commit with a scratch project; "by reading" means the source only.

## 1. What and why

Three refactorings an enum needs and no tool does: a group of `int` or `String` constants becomes
an enum (slice 1), an enum gains a field with a value per constant (slice 2), and a switch over an
enum becomes a method or a field of the enum (slice 3). The evidence is thin, and CLAUDE.md asks
for evidence before a tool: `docs/friction-log.md` has **no entry that mentions an enum**; the
only source is the coverage review of 2026-11-13 ("Enums: only `add_element`; nothing turns
constants into an enum or adds a field to one"), and this repo's own code has two enum
declarations. So this note is for choosing shapes, not a decision to build, as the loop sketch
was. What the runs did find is five places where today's tools fail on an enum that already
exists (section 7, "What already works, and what breaks"). Those are bugs with a reproduction,
not speculation, and they come first in the increments whatever is chosen for the rest.

## 2. Examples

Calls use the names section 3 recommends. Files are relative to the source root, as in goldens.

### Slice 1: constants to an enum

**1.1 Int constants, a field, a getter, a switch in another file.**

```json
{"tool": "extract_type", "file": "com/example/Order.java", "name": "Order",
 "members": ["OPEN", "SHIPPED", "CLOSED"], "new_type": "OrderState"}
```

```java
// Order.java, before
public class Order {
  public static final int OPEN = 0;
  public static final int SHIPPED = 1;
  public static final int CLOSED = 2;

  private int state = OPEN;

  public int state() { return state; }

  public void ship() {
    if (state == OPEN) {
      state = SHIPPED;
    }
  }
}
// Report.java, before
String label(Order o) {
  switch (o.state()) {
    case Order.OPEN: return "open";
    case Order.SHIPPED: return "on its way";
    default: return "done";
  }
}
```

```java
// OrderState.java, new, beside Order.java
public enum OrderState {
  OPEN,
  SHIPPED,
  CLOSED
}
// Order.java, after: the three constants are gone
public class Order {
  private OrderState state = OrderState.OPEN;

  public OrderState state() { return state; }

  public void ship() {
    if (state == OrderState.OPEN) {
      state = OrderState.SHIPPED;
    }
  }
}
// Report.java, after: case labels of an enum switch are written bare; the default stays
String label(Order o) {
  switch (o.state()) {
    case OPEN: return "open";
    case SHIPPED: return "on its way";
    default: return "done";
  }
}
```

Result: `made OPEN, SHIPPED and CLOSED of Order the enum OrderState (com/example/OrderState.java);
retyped Order.state and Order.state(); changed com/example/Report.java`.

**1.2 String constants of an interface, compared with `equals`.** `members` are `LOW` and `HIGH`
of `Levels`, `new_type` `Level`.

```java
// before
interface Levels { String LOW = "low"; String HIGH = "high"; }
boolean urgent(String level) { return Levels.HIGH.equals(level); }
void run() { if (urgent(Levels.LOW)) { page(); } }
// after: equals is kept as written (an enum's equals is identity, and a null level gives false
// as before)
boolean urgent(Level level) { return Level.HIGH.equals(level); }
void run() { if (urgent(Level.LOW)) { page(); } }
```

**1.3 Refused: the value is printed.** `Order` also has `log.info("order is " + state);`.

> state is an operand of string + at line 14 of com/example/Order.java, where it would print
> OPEN, not 0. Pass accessor (such as code) to keep the value there as state.code(), or change
> that use first.

**1.4 The same call with `"accessor": "code"`.** Every place that sees the value reads it back.

```java
public enum OrderState {
  OPEN(0),
  SHIPPED(1),
  CLOSED(2);

  private final int code;

  OrderState(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
// Order.java
log.info("order is " + state.code());
statement.setInt(2, state.code());        // was setInt(2, state)
```

**1.5 Refused: a value that comes from outside.** `state = row.getInt("state");`

> line 31 of com/example/Order.java assigns row.getInt("state") to state, which may be a value
> that none of OPEN, SHIPPED, CLOSED has. Convert it first in a method that returns one of the
> constants for each value (if (raw == 0) return OPEN; ...); that method is then retyped.

### Slice 2: a field on an enum

**2.1 A value per constant, an enum with no body.**

```json
{"tool": "add_parameter", "file": "com/example/Color.java", "name": "Color",
 "parameter": "hex", "type": "String",
 "values": {"RED": "\"#f00\"", "GREEN": "\"#0f0\"", "BLUE": "\"#00f\""}}
```

```java
// before
public enum Color {
  RED,
  GREEN,
  BLUE
}
// after
public enum Color {
  RED("#f00"),
  GREEN("#0f0"),
  BLUE("#00f");

  private final String hex;

  Color(String hex) {
    this.hex = hex;
  }

  public String hex() {
    return hex;
  }
}
```

Result: `added the field String hex to Color, set by its constructor and read by hex(); 3
constants pass their values`.

**2.2 An enum that has a constructor, and a constant with a body.** `value` is what the constants
not in `values` pass. Arguments: `parameter` `weight`, `type` `int`, `value` `1`, `values`
`{"LARGE": "3"}`.

```java
// before
public enum Size {
  SMALL("s"),
  LARGE("l") {
    @Override public boolean bulky() { return true; }
  };

  private final String code;

  Size(String code) {
    this.code = code;
  }

  public boolean bulky() { return false; }
}
// after: the body of LARGE stays; the assignment goes last in the constructor
public enum Size {
  SMALL("s", 1),
  LARGE("l", 3) {
    @Override public boolean bulky() { return true; }
  };

  private final String code;
  private final int weight;

  Size(String code, int weight) {
    this.code = code;
    this.weight = weight;
  }

  public int weight() {
    return weight;
  }

  public boolean bulky() { return false; }
}
```

**2.3 Works today, pinned by no golden.** `add_parameter` with `name` `Color`, `parameter`
`code`, `type` `String`, `value` `""` on `enum Color { RED, GREEN(); Color() {} }` gives
`RED(""), GREEN("");` and `Color(String code) {}` (checked by a run: "added String code to
Color.Color() in 1 declaration and 2 calls"). One value for every constant, no field.

**2.4 Refused: a constant without a value.**

> values has nothing for BLUE, and there is no value for the rest. Add BLUE to values, or pass
> value for every constant values does not name.

**2.5 Refused: several constructors.**

> Color has 2 constructors (Color(String), Color(String, int)), and each constant chooses one.
> add_parameter adds a field to an enum with at most one constructor; to add a plain parameter
> to one of them, name it: Color(String).

### Slice 3: a switch over an enum to a method

**3.1 Every case a constant expression: a field.**

```json
{"tool": "type_switch_to_method", "file": "com/example/Paint.java", "line": 5, "name": "label"}
```

```java
// Paint.java, before
String label(Color c) {
  return switch (c) {
    case RED -> "r";
    case GREEN, BLUE -> "cool";
  };
}
// Paint.java, after
String label(Color c) {
  return c.label();
}
// Color.java, after
public enum Color {
  RED("r"),
  GREEN("cool"),
  BLUE("cool");

  private final String label;

  Color(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
```

Result: `added the field String label to Color, read by label(); 3 constants pass their case's
value; the code at line 5 of com/example/Paint.java is now c.label()`.

**3.2 Cases that compute: a method in each constant's body.** `name` is `next`.

```java
// before
LocalDate next(Schedule s) {
  return switch (s) {
    case DAILY -> LocalDate.now().plusDays(1);
    case WEEKLY -> LocalDate.now().plusWeeks(1);
  };
}
// Schedule.java, after
public enum Schedule {
  DAILY {
    @Override
    public LocalDate next() {
      return LocalDate.now().plusDays(1);
    }
  },
  WEEKLY {
    @Override
    public LocalDate next() {
      return LocalDate.now().plusWeeks(1);
    }
  };

  public abstract LocalDate next();
}
// the switch: return s.next();
```

**3.3 A switch statement with a default, and a case that uses the selector.** `name` is `announce`.

```java
// before
switch (level) {
  case HIGH -> Pager.page(level);
  default -> Log.note(level.name());
}
// Level.java, after: the default is the enum's own method, the selector is this
public enum Level {
  LOW,
  HIGH {
    @Override
    public void announce() {
      Pager.page(this);
    }
  };

  public void announce() {
    Log.note(name());
  }
}
// the switch: level.announce();
```

**3.4 Refused: a case uses a local** (today's wording, from `TypeSwitch.usesOnly`):

> the case of RED uses rate, which on RED would not be there. Pass only what the pattern
> variable and constants give; a value of the method needs to be a parameter of the new method,
> which type_switch_to_method does not add yet.

("the pattern variable" would read "the switch's value" for an enum.)

**3.5 Refused: an old-style switch.** `case RED: return "r"; case GREEN: ...`

> each case must be an arrow case giving a statement or a block; the one at case RED: is not.

(today's wording, checked by a run.)

No tool turns colon cases into arrow cases, so this refusal names no fix that a tool does:
question 7.

## 3. Shape options

### Slice 1: where constants-to-enum lives

| | Option | Cost |
|---|---|---|
| A (Recommended) | `extract_type`, one more shape: `name` a class or interface, `members` its `static final` constants, `new_type` the enum. New optional argument `accessor`. | No new tool, and the arguments already mean this ("group members into a new type in its own file, every use following"). `as` is required today and means nothing here, so it becomes optional in the schema and required by the three other shapes at run time. The description gets a fourth bullet. Today the call is refused: "OPEN is static; extract_type moves instance fields." The work is a new class either way (`ExtractType` is 935 lines already). |
| B | A new tool, `constants_to_enum` (`file`, `name`, `members`, `new_type`, `accessor`). | Tool 41 of 50 (`ToolsTest.MAX_TOOLS`). Easiest to find by name, which is why the owner chose two loop tools on 2026-11-10; against "prefer an option on an existing tool". `convert_to_enum`, beside `convert_to_record`, would read as "this class becomes an enum", a different refactoring. |
| C | `change_type` of each field, parameter and return to an enum the model wrote first, with a map from constants to enum constants. | Not one refactoring: `change_type` leaves uses to the gate, so each call alone does not compile and the model must find every holder itself and batch them. The tool cannot check the value is not observed, since it never sees the whole set. |

### Slice 2: where field-on-an-enum lives

| | Option | Cost |
|---|---|---|
| A (Recommended) | `add_parameter` with `name` an enum, as `name` a record adds a component (#129, the owner's choice over a tool). New argument `values` (an object, constant to expression); `value` is the rest's. | Changes what `name: "Color"` does on an enum with one constructor: today a plain parameter (example 2.3), then a field with an accessor. The plain parameter stays reachable by `line`, or by the constructor with its parameter types. |
| B | A new tool `add_field` (gorefactor's name for #129). | The owner already chose against it for records; a class would then be the only kind left without it, and would have to be built or refused. |
| C | No tool: fix the bugs of section 7 so the composition works (`add_declaration` of the field, constructor and accessor, then `add_parameter` of the constructor). | Cheapest. Every constant still passes one `value`, so each constant's own value is a `replace_text` afterwards: n typed edits for n constants, which is the part worth a tool. |

### Slice 3: where enum-switch-to-method lives, and what it writes

| | Option | Cost |
|---|---|---|
| A (Recommended) | `type_switch_to_method` widened: the cases are enum constants where they were type patterns. | The name says "type"; a constant with a body is a type, a constant with a field value is not. Shares `usesOnly`, `asReceiver`, `body` and the result wording. `TypeSwitch` needs a second way to place a member (a constant's body). |
| B | A new tool `enum_switch_to_method`. | Tool 41 or 42 of 50, for the same arguments (`file`, `line`, `name`). |
| C | No tool: `extract_function` of the switch, then `function_to_method` onto the enum parameter, which leaves `switch (this)` in one method of the enum. | Two calls, no new code if `function_to_method` takes an enum (not checked; `MemberMove.typeNamed` finds classes only, so probably refused). The switch survives, so a new constant is still not forced to answer. |

What it writes:

| | Option | Cost |
|---|---|---|
| a (Recommended) | The tool chooses: a field when every case gives a compile-time constant (or `null`), else a method per constant. Optional `as` (`method` or `field`) to say which. | Two outputs to test. The field form is slice 2's edit plus one replaced switch, so it costs little once slice 2 exists. |
| b | Always a method per constant. | One output. `RED { @Override public String label() { return "r"; } }` for what Java programmers write as `RED("r")`; a later `method_to_field` would be needed. |
| c | `as` required. | The model decides what the tool can see for itself. |

## 4. The semantics

### Slice 1

**The constants.** `static final` fields of one class or interface, all `int` or all `String`,
each with a compile-time constant value, no two equal. Refused: another type (`long`, `char`:
later), two constants with one value ("CLOSED and DONE are both 2; a value would not say which
it is"), a constant used in an annotation (an enum is not a constant expression there).

**What is retyped: the holders.** Start from every use of a constant, by binding, in every file
that names the class (`Holding`). A variable or method joins the holders when a constant or
another holder is copied into it, compared with it, switched on with it as a label, passed for
it, or returned from it: fields, locals, parameters, method returns. A parameter or return takes
its whole family (`MethodFamily`, as `change_type` with `parameter` or `return_type`); a family
member that overrides a library method is refused with the existing wording. Repeat until
nothing joins.

**At each use.** Only these are allowed; each keeps its text but for the constant's name:

| Use of a constant or holder | After |
|---|---|
| `Order.OPEN`, a bare `OPEN`, a static import | `OrderState.OPEN`; imports fixed |
| a copy: initializer, assignment, argument of a project method, `return` | unchanged |
| `a == b`, `a != b`, both holders or constants | unchanged |
| `a.equals(b)`, `Objects.equals(a, b)`, both holders or constants | unchanged |
| `switch (holder)` with constants as labels | labels written bare (`case OPEN`); `default` kept |
| `holder == null` (a `String`) | unchanged |
| a branch of `?:`, parentheses | as the whole |
| a literal equal to one constant's value copied into or compared with a holder (`state = 0`) | question 2 |

**Everything else is refused, by name and line,** because most of it still compiles with an enum
and does something else. This is a list of what is allowed, not of what is risky: `println(state)`
picks another overload, `"" + state` prints the name, `map.get(state)` and `list.contains(state)`
take `Object` and find nothing, `state.equals("open")` is false, `list.remove(state)` no longer
removes by index. Where the old value is seen:

| Seen by | Example | Without `accessor` | With `accessor: "code"` |
|---|---|---|---|
| printing, string `+`, `String.valueOf`, `format` | `"state " + state` | refused | `state.code()` |
| arithmetic, bit operations, `++` | `OPEN + 1`, `READ \| WRITE` | refused; flags say so ("READ is an operand of \|; flags are a set, not one enum value") | `.code()` on each operand |
| ordering | `state < CLOSED` | refused | `state.code() < OrderState.CLOSED.code()` |
| a library call, a cast, an array index | `ps.setInt(2, state)`, `names[state]` | refused | `.code()` |
| a collection or array of them | `List<Integer> open`, `int[] states` | refused | the collection keeps its type; `open.add(state.code())` |
| a `HashMap` key | `Map<Integer, X>` | refused: the map would also iterate in another order | as above |
| a record component, or a class whose `toString` is generated | `record Line(int state)` | refused without `value_semantics=true`: its `toString` and `hashCode` change | the same flag |
| reflection: serialization, JSON, an ORM | a field read by name | **not seen by the tool.** The result names each retyped field, and a field with an annotation, or of a class that implements `Serializable`, is refused without `value_semantics=true` | the same |

**Values coming in.** A holder that is given anything but a constant, a holder or `null` is
refused (example 1.5): an `int` can hold 7 and an enum cannot. A field holder with no initializer
starts as 0: when a constant is 0 the tool writes `= OrderState.OPEN`, otherwise it is refused
("state starts as 0, which is none of the constants. Give it an initializer first"). A `String`
field starts `null` before and after.

**`null`.** A switch on a `null` enum throws, and an `int` cannot be `null`. After the change an
`int` holder still cannot: every value that reaches it is a constant, and unset fields are
handled above. A `String` holder could be `null` before, and its switch threw then too. With
`accessor`, `s.code()` on a `String` holder that may be `null` (a `null` is copied into it, or a
parameter of a public method) throws where `"" + s` printed "null": reported through
`Exceptional.change`, refused under `preserve_exceptions`.

**Switch.** A switch statement keeps its meaning with or without `default`: neither kind must
cover every value. A switch expression over an `int` must have a `default`; it stays, even when
the cases now cover every constant, so a constant added later still goes there. A label that is
not a constant or a literal equal to one (`case OPEN + 1`, `case 7`) is refused.

**The declaration.** The enum goes in a new file beside the class, public when the class is, the
constants in declaration order with their Javadoc; the old constants are deleted. Names are kept
(`STATE_OPEN` stays; `rename` after). Refused: a file of that name, a constant used in a file the
project does not own (nothing to check: as every signature tool, the project is the world).

**Flags.** `value_semantics=true`, as `convert_to_record`: accepts the generated `toString` and
`hashCode` of a record that holds one, and the fields reflection may read. `accessor` changes no
behavior; it is the bridge. `preserve_exceptions` as everywhere.

### Slice 2

`add_parameter` with `name` an enum of `file`, `parameter`, `type`, and `value` or `values`:

- **Field:** `private final <type> <parameter>;`, placed by `Placement` (after the last field).
- **Constructor:** none declared: one is written, taking the parameter. One declared: the
  parameter is added (last, or `before`) and `this.<parameter> = <parameter>;` goes last in its
  body. Several: refused (2.5).
- **Accessor:** `public <type> <parameter>()` returning the field (question 4).
- **Constants:** each passes its entry of `values`, else `value`: `RED` becomes `RED("r")`,
  `RED()` `RED("r")`, `RED(1)` `RED(1, "r")`; a body after the constant stays. This is
  `SignatureChange.insert` with `bare`, which exists (2.3).
- **Separator:** an enum with constants only gets its `;` after the last constant.

Behavior: nothing reads the field, so nothing changes; a value is evaluated once, when the enum
is initialized, so a value with an effect is refused as `add_parameter`'s `value` is. The gate
checks each value compiles in the enum (a forward reference to a static field of the enum is a
compile error it will name).

Refusals: 2.4, 2.5; a key of `values` that is no constant ("Color has no constant PURPLE; its
constants are RED, GREEN, BLUE"); a field, parameter or no-argument method of that name
("Color already has a field hex"); `name()`, `ordinal()` and the other methods of `Enum`
("Color inherits ordinal() from Enum; choose another name").

Compared with what exists:

| | `add_parameter` on a constructor | on a record (#129) | `add_declaration` `constructor` | this |
|---|---|---|---|---|
| declares the field | no | yes (a component) | no: takes fields that exist | yes |
| assigns it | no | yes (canonical) | yes | yes |
| accessor | no | yes (generated) | no | yes |
| uses updated | every `new`, `this(...)`, `super(...)`, enum constant | every `new`, `this(...)` | none | every constant |
| value | one `value` | one `value` | none | `values` per constant, `value` for the rest |
| on an enum today | works for constants without a body | not applicable | refused: "Color is not a class; constructor writes a class's constructor" | not built |

### Slice 3

The switch on `line` (expression or statement, arrow cases) whose selector's type is an enum of
the project; `name` the new member.

- **Field form** (every case's expression has a constant value, `resolveConstantExpressionValue`,
  or is `null`): slice 2's edit with `values` from the cases. A value is then computed when the
  enum loads, not at each call, which is why only constants qualify.
- **Method form:** each case's expression becomes `return <expr>;`, its statements the body, in
  `@Override public R name()` in that constant's body; a constant without a body gets one, a
  constant with one gets the method appended. The enum declares `public abstract R name();`, or
  with a `default` the default's body as its own method.
- **The switch** becomes `e.name()` (`this.name()` is written `name()`).

| Case | Rule |
|---|---|
| `default` | method form: the enum's own method, constants without a case inherit it. Field form: its value for every constant no case names; refused when it is not a constant (`default -> throw ...`), naming `as: "method"` |
| no `default`, not every constant (a statement only; an expression must cover them) | the enum's own method has an empty body; in the field form, refused |
| `case RED, GREEN ->` | field form: each passes the value. Method form: question 6 |
| fall-through, colon cases | refused today for every colon case; question 7 |
| `yield` | a block of a switch expression ending in `yield x;` becomes a body with `return x;` for each `yield` of that switch; today a block there is refused ("each case must be an arrow case giving an expression") |
| `return`, `break`, `continue` leaving a case | refused, today's wording |
| a local, a parameter, `this`, a member of the method's class | refused, today's wording (3.4); adding them as parameters is its own roadmap item, for types and enums alike |
| the selector's own name in a case | `this`, as in today's default case |
| `case null` | refused: the method cannot be called on `null` |
| a `null` selector | throws `NullPointerException` at the switch before, at the call after: the same |
| a constant whose body declares `name()`; the enum declares or inherits it (`name`, `ordinal`, `toString`) | refused: "RED already declares label()", "Color inherits name() from Enum; choose another name" |
| an enum nested in a class | refused today by `TypeSwitch.fileOf` and `declaration` (top-level types only); common for enums, so worth lifting: increment 8 |

No flag is needed: nothing here has to change behavior. The case bodies move to another type, so
names in them may mean something else there (a bare `OTHER` is one of the enum's constants
inside the enum). `TypeSwitch` runs no `NameCheck` today (by reading); the enum form should, and
it needs probes.

## 5. Open questions for the owner

1. **How much to build now?** (a) Recommended: the fixes and pins of increments 1 to 3 (bugs
   with reproductions), then slice 2, then slice 3; slice 1 waits for a friction entry or a task
   that needs it, since it is the largest and has no evidence. (b) All three slices, in the order
   of section 6. (c) Only the fixes; log enum friction and decide then.
2. **Slice 1, a literal where a constant is meant** (`state = 0`, `case 1:`). (a) Recommended:
   a literal equal to exactly one constant's value, copied into or compared with a holder,
   becomes that constant, and the result lists each; costs a rule that guesses intent, though the
   value is the same. (b) Refuse every literal, naming `replace_expression` to write the
   constant first; more calls, no guess.
3. **Slice 1, values from outside** (a database read, a parsed number). (a) Recommended: refuse,
   naming the conversion method to write first (1.5). (b) With `accessor`, also write
   `static OrderState of(int code)` and wrap each such value; a value no constant has then
   throws where it used to pass through, a normal-flow change, so it would need its own flag.
   (c) As (b) but returning `null`; fails later and further away.
4. **Slice 2, what `name` an enum means, and the accessor.** (a) Recommended: always the field
   with `private final`, the constructor and a record-style accessor `hex()`; a plain constructor
   parameter by `line` or `Color(String)`. (b) The field only when `values` is passed; `value`
   alone stays a plain parameter as today; two meanings of one call. For the accessor:
   record-style (recommended, the enum then reads like a record), `getHex()` as
   `encapsulate_field` writes, or none (then `encapsulate_field` must first take an enum; today
   "Color is not a top-level type of ... that resolves").
5. **Slice 1's home:** `extract_type` (recommended), a new tool, or `change_type` (section 3).
6. **Slice 3, one case for several constants, method form.** (a) Recommended: copy the body into
   each constant and say so in the result; duplicates code that `fold` or a later edit can
   merge. (b) Refuse when the body is more than one expression, naming the field form or a
   split of the case. (c) One private static method on the enum that each constant calls; more
   code written, no duplication.
7. **Slice 3, old-style switches** (`case RED: return "r";`), the usual shape in older code.
   (a) Recommended: a later increment that takes a switch statement whose every group ends in
   `return <expr>;` or `throw`, as `return e.name();`. (b) A general colon-to-arrow conversion
   first (a new option somewhere, perhaps `convert_loop`'s sibling), and this tool stays
   arrow-only. (c) Leave refused.
8. **The inverse** (a method per constant, or a field, back to one switch). (a) Recommended: not
   now; no evidence, and `inline` of a method with one implementation per constant is a
   different tool's job. (b) `as: "switch"` on the same tool, when a first use needs it.

## 6. Increments, one PR each

1. **A member added to an enum that has constants only.** One helper that writes the `;` after
   the last constant, used by `Declarations.add` (its end-of-type branch), `MemberMove.appendTo`
   and `addTo`, and `Stubs`' field stub. Goldens: `add_declaration_enum_first_member`,
   `add_declaration_enum_one_line`, `stub_field_in_enum`, `type_switch_case_type_enum`. No probe
   (nothing moves that did not before).
2. **`add_parameter` reaches a constant with a body.** `MethodFamily`'s
   `visit(EnumConstantDeclaration)`. Goldens: `add_parameter_enum_constants` (pins 2.3),
   `add_parameter_enum_constant_body`, `remove_parameter_enum_constants`.
3. **Pins for what works:** goldens `rename_enum_constant_case_label` (the constant, a qualified
   use and a `case` label in another file), `add_declaration_constructor_refused_enum` with a
   wording that names what to send for an enum. No code beyond the refusal text.
4. **Slice 2:** `add_parameter` with `name` an enum and `values` (`Signatures.addField`, beside
   `addComponent`; `Arg.json` for `values`; `Tools.ADD_PARAMETER`'s description). Goldens:
   `add_parameter_enum_field`, `_enum_field_existing_constructor`, `_enum_field_constant_body`,
   `_enum_field_refused_missing_value`, `_refused_two_constructors`, `_refused_inherited_name`.
   Probe `add_parameter_enum_field_unchanged` (prints `values()` and each `toString` before and
   after).
5. **Slice 3, field form:** `TypeSwitch` reads enum cases, writes through increment 4. Goldens:
   `type_switch_enum_field`, `_enum_field_default`, `_enum_field_shared_case`,
   `_enum_refused_local`, `_enum_refused_case_null`. Probes: `type_switch_enum_field` (every
   constant printed), `type_switch_enum_null_selector` (the same exception before and after).
6. **Slice 3, method form:** bodies in constants (`MemberMove` gains "append to a constant's
   body, creating it"), `NameCheck` on each moved body. Goldens: `type_switch_enum_method`,
   `_enum_method_default`, `_enum_statement`, `_enum_statement_partial`, `_enum_existing_body`,
   `_enum_refused_declared`. Probes: `type_switch_enum_method_default`,
   `type_switch_enum_name_capture` (a case that names a static the enum also has).
7. **Slice 3, blocks with `yield`.** Goldens `type_switch_enum_yield`, `_refused_yield_outer`;
   probe `type_switch_enum_yield`.
8. **Slice 3, nested enums and nested case types** (`TypeSwitch.fileOf`, `declaration`).
   Golden `type_switch_enum_nested`.
9. **Slice 1, the core:** constants, holders, `==`, `equals`, `switch`, copies, in one class and
   across files; everything else refused. New class `ConstantsToEnum`, entered from
   `ExtractType.run`; uses `Holding`, `MethodFamily`, `SignatureChange.replace`, `Imports.fix`,
   `Gate.applyOrRefuse`. Goldens: `extract_type_constants_enum`, `_enum_string_equals`,
   `_enum_switch_other_file`, `_enum_parameter_family`, `_enum_refused_printed`,
   `_enum_refused_arithmetic`, `_enum_refused_incoming`, `_enum_refused_collection`,
   `_enum_refused_duplicate_value`, `_enum_refused_unset_field`. Probes, one per row of the
   "seen by" table that compiles after a naive retype: `constants_enum_println`, `_map_get`,
   `_list_remove`, `_equals_literal`, `_switch_default`; each must be refused, or print the same.
10. **Slice 1, `accessor`.** Goldens `extract_type_constants_enum_accessor`,
    `_accessor_ordering`, `_accessor_library_call`; probe `constants_enum_accessor_printed`;
    `exceptional.txt` for a `String` holder that may be `null`.
11. **Slice 1, literals and `value_semantics`** (questions 2 and the record row). Goldens
    `_enum_literal`, `_enum_refused_record`, `_enum_record_value_semantics`.
12. **Slice 3, old-style switches**, if question 7 says so.

`RefactoringStressTest` on Commons Lang after 6 (every switch over a project enum) and after 9
(every group of `static final int` in one class), counting applied and refused by reason.

## 7. Risks, and what the runs found

**What already works, and what breaks** (each checked by a run on a copy of 63c9cde):

| Call on an enum | Today |
|---|---|
| `add_parameter` on its constructor, constants bare or with `()` | works; `RED` becomes `RED("")`. No golden. The contract lists "enum constants" under signature changes |
| the same, a constant with a body | **refused by the gate:** "The constructor Color() is undefined". `MethodFamily` matches a constant by `resolveConstructorBinding()`, which for a constant with a body is apparently its anonymous class's (by reading) |
| `add_parameter`, no constructor declared | refused: "Color declares no constructor. Add one with add_declaration (constructor), then change it." **That fix is itself refused:** "Color is not a class; constructor writes a class's constructor (a record has its canonical one)." |
| `add_declaration` of any member, an enum with constants only | **refused as a syntax error:** "Syntax error on token "GREEN", ; expected after this token". The member is placed after the constants and no `;` is written |
| an edit that reads `Size.HUGE`, which nothing declares | **refused, naming a file the model did not touch:** the stub `static ... HUGE; // not implemented` is written into the enum without the `;` |
| `rename` of a constant | works, `case` labels in other files too. No golden |
| `inspect` with `names: ["Size.SMALL"]` | works |
| `add_element` of `BLUE("b")` with no such constructor | refused by the gate; no constructor stub for an enum |
| `type_switch_to_method` where a case's type is an enum that implements the interface (`case Unit u ->`) | **refused by the gate with a syntax error** in the enum's file: the method is appended without the `;` |
| `type_switch_to_method` on an enum switch | refused: "each case must be a type pattern (case Circle c -> ...); the one at case SMALL -> is not." |
| `encapsulate_field`, `change_type` of an enum's field | refused: "Color is not a top-level type of ... that resolves." (`Opened.topLevel`, `MemberMove.typeNamed` find classes) |
| `move` of a static member into an enum | refused: "Color is not a class of ..." (already a follow-up in `docs/sessions/split-placement.md`) |
| `extract_type` of constants | refused: "OPEN is static; extract_type moves instance fields." |

**Where the code assumes a class:**

- `MemberMove.typeNamed` returns a `TypeDeclaration`; `Opened.topLevel`, `PullUp`, `MoveMember`,
  `AddResult`, `InlineClass` and `SubclassToStrategy` go through it. Widening it to
  `AbstractTypeDeclaration` touches each caller's assumptions (`getFields()`, `getMethods()`,
  `isInterface()` are `TypeDeclaration`'s). Not needed for slices 2 and 3; needed if the accessor
  question picks `encapsulate_field`.
- `Placement.place` and `MemberMove.appendTo` look only at `bodyDeclarations()`. An enum's
  constants are not among them, so "the type has no members" is true of `enum Color { RED }`.
  In `appendTo` the branch for a body on one line replaces the text from `{` to `}`, which on
  `enum Unit implements Shape { A, B }` drops the constants (by reading). It is reachable today:
  `type_switch_to_method` with `case Unit u ->` is refused by the gate with "Syntax error on
  token "int", delete this token" in `Unit.java` (checked by a run). The gate saves it; increment
  1 should fix and pin it (`type_switch_case_type_enum`).
- `TypeSwitch.fileOf` and `declaration` take top-level types only; enums are often nested.
- `TypeSwitch` writes `"@Override\n  public "` with two-space indentation and places members by
  `appendTo`, not `Placement`; a constant's body needs its own indentation (`memberIndent` reads
  the type's members, not a constant's).
- `FromFields.constructor` refuses anything but a class, and an enum's constructor must not be
  `public`: it copies "the class's access" today.
- `Stubs` declares no constructor for an enum constant's arguments and no constant for a `case`
  label; out of scope here, but increment 4's own edits must not rely on stubs.
- `PrimitiveUses.risky` is the nearest thing to slice 1's "seen by" list, but it lists what is
  risky. Slice 1 needs the opposite, a list of what is allowed, because the dangerous uses are
  the ones that take `Object`. Reusing it as is would let `map.get(state)` through.
- `Imports.fix` after slice 1: a static import of a deleted constant must go, and bare `OPEN`
  inside the old class must gain `OrderState.`. Whether `Imports.fix` removes a static import of
  a field that no longer exists was not checked.
- An enum `case` label must be bare below Java 21 and may be qualified from 21. The tool should
  always write it bare; the build's `maven.compiler.release` is what the gate compiles with.
- Slice 1's holders are found by flow within the project's source. A holder reached only through
  a lambda, a generic method (`T pick(T a, T b)`) or a `var` is where the closure is easiest to
  get wrong; the first version should refuse a holder that passes through a type variable or a
  lambda parameter and let the stress run say how often that is.

## Parity

Names and arguments go in `docs/tool-contract.md` first. Go has no enum type: slice 1 there is
"untyped constants to a named type with `iota`", the same `extract_type` shape with other output;
slices 2 and 3 have no Go form (a method on the named type holding the switch is all there is).
TypeScript has enums and unions of literals, no per-constant bodies or fields: slice 1 applies,
slices 2 and 3 become a lookup object.
