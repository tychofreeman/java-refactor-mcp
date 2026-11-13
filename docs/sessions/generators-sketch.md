# Statement and expression generators: a sketch

Status: a sketch for choosing the shape, not a decision to build. Written 2026-11-13 against
`main` at 63c9cde. Nothing here is built. Where a claim about the code was read and not run, it
says so.

Decided 2026-11-13: the owner chose the Recommended option of every open question in section 6
(`docs/design.md`, Decisions). Slice 4 is built from roadmap #147 on; the rest is as written.

## 1. What and why

The tools move and reshape code; none builds a statement or an expression from names. A new
loop, call, assignment or guard is typed as `content` (or `new`) into `insert_statements`,
`replace_statements`, `replace_expression` or `add_declaration`. The coverage review of
2026-11-13 counted the session records: in the 29 records with step counts, 540 of 560 tool
steps (96%) were edits where the model typed the Java. `docs/design.md` ("Constructive
transformations") says a model should think in structures and type only the idea, and its table
has "surround with" as the only statement-level command. gorefactor's roadmap item 13
("Generators") plans the same step: "a batch carries names and expressions rather than
statements", with `create_loop` and `replace_parameter` first.

Discoverability decides whether any of this is used. `implement`, `constructor`, `surround` and
`test_for` went unused in the first three A/B sets (`ab-shop-tasks`, `ab-constructive-ops`,
`ab-library`): "options inside descriptions are not found". They were used once a refusal named
them (`ab-library-skeleton`: `constructor` twice, `surround` once, each in the call after the
refusal), at the cost of a turn per steer, and typed lines did not fall (425 against 409).
`test_for` was the one option called unprompted. So this sketch treats "how does a model find
it" as part of the shape (section 3), and section 5 counts what a generator saves, which is
less than the thesis suggests.

Four slices: a loop from names, a call or construction, a guard and its exit, and
`replace_parameter`. The first three are edits. The fourth is a refactoring.

## 2. Examples

The calls use the recommended shape of section 3: one argument, `build`, in place of `content`
on `insert_statements` and `replace_statements` (a list whose entries are generator objects or
typed statements), and in place of `new` on `replace_expression` (one object). Section 3 gives
the same calls in the other shapes.

### Loop from names

**1. A for-each over a field, the element's type and name from bindings**

```json
{"tool": "insert_statements", "args": {"file": "shop/Ledger.java", "line": 14,
  "build": [{"loop": "orders"}]}}
```
```java
// before                                   // after (orders is a List<Order>)
Money total = Money.zero();                 Money total = Money.zero();
return total;                               for (Order order : orders) {
                                            }
                                            return total;
```
Result: `inserted 2 lines after the statement at line 14 of shop/Ledger.java; built for (Order
order : orders)`. An empty loop is of little use alone; example 2 is the usual call.

**2. With a body: typed statements, or more generators, which may use the element**

```json
{"tool": "insert_statements", "args": {"file": "shop/Ledger.java", "line": 14,
  "build": [{"loop": "orders", "name": "o", "body": ["total = total.plus(o.amount());"]}]}}
```
```java
for (Order o : orders) {
  total = total.plus(o.amount());
}
```

**3. A map: the entry's type is the long part**

```json
{"build": [{"loop": "byCustomer", "body": ["report.add(entry.getKey(), entry.getValue().size());"]}]}
```
```java
// byCustomer is a Map<String, List<Order>>
for (Map.Entry<String, List<Order>> entry : byCustomer.entrySet()) {
  report.add(entry.getKey(), entry.getValue().size());
}
```
`java.util.Map` and `List` are imported if the file lacks them (`Imports.fix`, as every edit).

**4. An index loop; and wrapping statements that exist**

```json
{"build": [{"loop": "names", "form": "index"}]}
```
```java
for (int i = 0; i < names.length; i++) {     // names is a String[]; names.size() for a List;
}                                            // i < count for an int
```
```json
{"tool": "replace_statements", "args": {"file": "shop/Ledger.java", "start_line": 20,
  "end_line": 21, "surround": {"loop": "orders"}}}
```
`surround` takes the object where it takes a typed head today (`"for (Order order : orders)"`);
the statements of lines 20-21 go inside, as `Surround.wrap` does now.

**5. Refused**

```json
{"build": [{"loop": "total"}]}
```
`refused: total is a Money, which a loop cannot walk: loop takes an array, an Iterable, a Map,
or (with form index) an int. Nothing was changed.`

```json
{"build": [{"loop": "orders", "name": "total"}]}
```
`refused: total is already a local here (declared at line 13), so the loop's variable cannot
have that name. Pass another name, or leave name out and it is order.`

### Call or construction

**6. A call whose result is declared: the local's type from the method**

```json
{"tool": "insert_statements", "args": {"file": "shop/Checkout.java", "line": 31,
  "build": [{"call": "pricing.priceOf", "args": ["order", "customer"], "into": "price"}]}}
```
```java
Money price = pricing.priceOf(order, customer);
```
Result: `inserted 1 line after the statement at line 31 of shop/Checkout.java; built Money
price; imported shop.money.Money`.

**7. `into` a name already in scope: an assignment. Without `into`: a statement**

```json
{"build": [{"call": "total.plus", "args": ["price"], "into": "total"},
           {"call": "audit.record", "args": ["order"]}]}
```
```java
total = total.plus(price);
audit.record(order);
```

**8. A construction; the declared type given when it is not the class**

```json
{"build": [{"new": "ArrayList", "into": "open", "type": "List<Order>"}]}
```
```java
List<Order> open = new ArrayList<>();        // imports java.util.ArrayList, java.util.List
```
Without `type`: `Receipt receipt = new Receipt(order, price);` from `{"new": "Receipt", "args":
["order", "price"], "into": "receipt"}`.

**9. As an expression**

```json
{"tool": "replace_expression", "args": {"file": "shop/Checkout.java", "line": 40,
  "expression": "price", "build": {"call": "discounts.apply", "args": ["price", "customer"]}}}
```
```java
return price;                         // before
return discounts.apply(price, customer);
```

**10. Refused, and a stub**

```json
{"build": [{"call": "pricing.priceOf", "args": ["order"], "into": "price"}]}
```
`refused: Pricing.priceOf takes (Order order, String customer); args has 1 of 2. Pass each, in
that order. Nothing was changed.`

```json
{"build": [{"call": "audit.record", "args": ["order"], "into": "entry"}]}
```
`refused: Audit.record(Order) returns nothing, so there is nothing to put in entry. Send the
call without into.`

A method nothing declares gets a stub, as in any edit, but the stub's result type comes from
its use, and a generated declaration has no type yet:
`refused: Pricing.quote is not declared, so the type of quote's result is not known. Pass type
(the local's type): the call is then written and Pricing.quote(Order) is declared as a stub
returning it.`

### Guard and return

**11. A condition and an exit**

```json
{"tool": "insert_statements", "args": {"file": "shop/Ledger.java", "line": 16, "where": "before",
  "build": [{"guard": "!order.customer().equals(customer)", "exit": "continue"}]}}
```
```java
if (!order.customer().equals(customer)) {
  continue;
}
```
The condition is the typed idea. What the tool adds is the block, the braces as the file
writes them, and the checks below (a `continue` outside a loop, a `return` that needs a value).

**12. Null checks**

```json
{"build": [{"guard_null": "customer", "exit": "require"}]}
```
```java
Objects.requireNonNull(customer);            // imports java.util.Objects
```
```json
{"build": [{"guard_null": "order.coupon()", "exit": "return", "value": "price"}]}
```
```java
if (order.coupon() == null) {
  return price;
}
```

**13. A throw**

```json
{"build": [{"guard": "quantity <= 0", "exit": "throw", "value": "IllegalArgumentException",
            "message": "\"quantity must be positive: \" + quantity"}]}
```
```java
if (quantity <= 0) {
  throw new IllegalArgumentException("quantity must be positive: " + quantity);
}
```

**14. Refused**

```json
{"build": [{"guard": "orders.isEmpty()", "exit": "return"}]}
```
`refused: Ledger.totalFor(String) returns Money, so return needs a value. Pass value: what to
return when orders.isEmpty().` (No default is guessed: zero, null and an empty list are all
plausible and all behavior.)

`{"guard": "done", "exit": "continue"}` outside a loop: `refused: line 12 of shop/Ledger.java is
not inside a loop, so there is nothing to continue. The exits here are return and throw.`

`{"guard_null": "count", "exit": "require"}`: `refused: count is an int, which is never null.`

`{"guard": "file == null", "exit": "throw", "value": "IOException"}` in a method that does not
declare it: `refused: Loader.load(Path) does not declare IOException. Send change_signature
with add_throws IOException before this step in the same batch (its callers follow), or throw
an unchecked exception.`

### replace_parameter

A refactoring, shown as `remove_parameter` with `value` widened from a field or a literal
(#315, built) to an expression of the other parameters. See section 4 for why this is not quite
what gorefactor calls `replace_parameter`.

**15. A parameter every call computes from another one**

```json
{"tool": "remove_parameter", "args": {"file": "shop/Pricing.java", "name": "Pricing.priceOf",
  "parameter": "customer", "value": "order.customer()"}}
```
```java
// before
Money priceOf(Order order, String customer) {
  return rates.rateFor(customer).times(order.amount());
}
... pricing.priceOf(order, order.customer());
... pricing.priceOf(cart.order(), cart.order().customer());

// after
Money priceOf(Order order) {
  String customer = order.customer();
  return rates.rateFor(customer).times(order.amount());
}
... pricing.priceOf(order);
... pricing.priceOf(cart.order());
```
Result: `removed customer from Pricing.priceOf(Order, String) in 1 declaration and 2 calls
(shop/Pricing.java, shop/Checkout.java); its body starts with String customer =
order.customer()`.

**16. Arithmetic on other parameters (the inverse of `extract_variable` `as=parameter`)**

```json
{"tool": "remove_parameter", "args": {"file": "shop/Box.java", "name": "Box.fits",
  "parameter": "area", "value": "width * height"}}
```
```java
boolean fits(int width, int height, int area) { return area <= LIMIT; }   // before
... fits(w, h + 1, w * (h + 1))
boolean fits(int width, int height) {                                     // after
  int area = width * height;
  return area <= LIMIT;
}
... fits(w, h + 1)
```
The call's argument `w * (h + 1)` is `width * height` with that call's arguments in place of
the parameters, so it matches.

**17. Refused: a call that passes something else**

`refused: the call at shop/Admin.java line 44 passes guest, not order.customer() (with that
call's arguments: basket.customer()), so customer cannot become order.customer(). Change that
call first, or keep the parameter. Nothing was changed.` As #315's refusal, which this widens.

**18. Refused: effects**

`refused: the call at shop/Feed.java line 12 passes next() for order, and order.customer()
would then be computed from one next() where the call ran two. Put next() in a local first.`

`refused: the call at shop/Feed.java line 19 passes log.mark() after order.customer(); in the
method, order.customer() would run after log.mark(), not before it. Put log.mark() in a local
first, or move the parameter last (move_parameter).`

**19. Refused: a value the method cannot compute**

`refused: value may use Pricing.priceOf's other parameters (order), fields, constants and
literals; region is none of these there.`

## 3. Shape options

The same generators, three places to put them, and a fourth option that builds none.

**(a) An argument on the existing edit tools (Recommended).** `build` in place of `content` on
`insert_statements` and `replace_statements`, in place of `new` on `replace_expression`;
`surround` takes a loop object beside its typed head; `remove_parameter` `value` widens. No new
tool (40 published, limit 50 in `ToolsTest`).

- For: CLAUDE.md's rule ("prefer an option on an existing tool"), and the owner's choices so
  far: `surround`, `implement` and `constructor` as options "over three new tools" (#123),
  `parameterize` as `extract_variable` `as=parameter` (#125), `value` on `remove_parameter` over
  a `parameter_to_field` tool (#315). The place, the indentation, the gate, stubs and imports
  are those tools' already (`Primitives`, `Edits.gated`), so a generator is only the text.
- For: a list nests. One step carries a method body, generators and typed fragments mixed, and
  an inner entry can use what an outer one declares (the loop's element). Section 5: the
  nested step is 547 characters; the same body as one step per generator is about 1,000.
- For: it solves the empty block. `insert_statements` needs a statement to anchor on
  (`Opened.startingOn` with a parent `Block`), so nothing can be inserted into a loop that was
  just generated empty; `body` puts it there.
- Against: the A/B finding. An option in a description is not found; three run sets showed
  it. It needs a mechanism, and the one that has worked is a refusal that names the option,
  under `JREFACTOR_SKELETON_ONLY` (`Generated`), at a turn per steer. Open question 2.
- Against: the argument's schema is an object list (`Arg.json`, as `batch`'s `steps`), looser
  than flat arguments; a wrong key is the tool's refusal to word, not the schema's.

**(b) One new tool, `build_statements` (or `generate`), with a `kind`.** Arguments: `file`,
`line`/`where` or `start_line`/`end_line`, `kind` (`loop`, `call`, `new`, `guard`), and the
kind's own arguments.

- For: one name in the tool list; a model reads tool names more reliably than option text
  (`inline` and `move` were found at once after the merge, `ab-merged-tools`).
- Against: the name says nothing a model is about to do ("I want a loop" does not lead to
  `generate`); `test_for` was found because its name was the intent. The flat arguments are a
  union (`over` only for a loop, `into` only for a call), so most combinations are refusals.
  One more tool to describe, and the placement arguments of `insert_statements` and
  `replace_statements` copied. No nesting unless it also takes a list, which is (a) under
  another name.

**(c) One tool per generator, gorefactor's names:** `create_loop`, `create_call`,
`create_guard`, `replace_parameter` (44 tools).

- For: the names are the intent, the best case for being found without a refusal; parity with
  gorefactor's planned `create_loop` and `replace_parameter`; flat, strict schemas.
- Against: four tools against CLAUDE.md's rule and the tool budget; each repeats `file`,
  `line`, `where`. A method body is one step per statement, each anchored on the last
  (`$prev.end`), and a statement inside a generated loop still has no anchor. Measured in
  section 5: about twice the characters of typing the Java.

**(d) No generators: inference inside typed content.** The model types `var price =
pricing.priceOf(order, customer);` or `for (var order : orders)`, and an option (per call or per
project) has the edit write the explicit type. Nothing to discover, like stubs and imports.

- For: it delivers the part of slices 1 and 2 that bindings give (types, imports) with no new
  vocabulary and no extra turn.
- Against: the model still types statements, so it does nothing for the thesis; `var` is valid
  Java that a project may want kept; nothing for guards or `replace_parameter`.

**Recommendation: (a), built so that (c) is a measurement away.** The generators are one class
(`Build`, section 7) that takes an entry and returns text; `build` hands it a list. Gorefactor's
names can then be unpublished aliases from the start (`Tools.ALIASES`, as `extract_method` and
`method_to_function` are): `create_loop` with `over` routes to `insert_statements` with one
`loop` entry, `replace_parameter` to `remove_parameter` with `value`. An A/B arm that publishes
the aliases against one that publishes only `build` then answers "option or tool" with runs
rather than argument. (d) is worth keeping in mind as the cheap fallback if the runs show
`build` unused and the aliases too.

## 4. Per slice

Shared by slices 1-3:

- **Where the types come from.** The tool writes its text into a copy of the file with `var`
  where a type is not yet known, parses that copy with bindings (`Jdt.parse(project, file,
  source)`), reads each `var`'s resolved type, and writes it with `Extract.typeName(type, at)`,
  the way `extract_variable` and `convert_loop` name types. One parse covers a nested list. Not
  yet checked: that JDT resolves `var` in an enhanced `for` and a local under bindings recovery
  at source level 21 as javac does, and what it gives for a diamond (`var x = new
  ArrayList<>()` is `ArrayList<Object>`, hence `type` in example 8). The fallback is to read the
  expression's type and compute from it (`LoopToStream.elementOf` for an element).
- **Explicit types, not `var`,** as every tool here writes (open question 4). A type that
  cannot be written (anonymous, an intersection) is refused, naming `type`.
- **Names the model gives are kept or refused, never changed.** A given name that a local in
  scope has, or that would hide a field the rest of the method reads (the rule
  `ExtractVariable` has), is refused naming both. A name the tool chose is made free with
  `LoopToStream.unused`.
- **Edits.** They change behavior on purpose, pass the type-check gate, get stubs and imports,
  and take `imports` for an ambiguous name. `Kind.EDIT`, so no `preserve_exceptions`.
- **Result.** The tool's usual line, then what was inferred, since the model did not type it
  and the next step needs it: `; built for (Order order : orders); Money price; total
  (assigned)`. One item per declaration or head, no body text.
- **In a batch.** A later step's `build` may use what an earlier step wrote. Read, not run:
  each step's text is on disk before the next step parses (`Introduce.parameter` reopens the
  file after `Signatures.addParameter`). Under check `"end"`, a name an earlier step left for a
  stub is not declared yet, so its type is unknown: the refusals below that say "pass type"
  cover it.
- **`content` and `build` together** are refused: "takes content or build, not both", as
  `surround` is.

### Slice 1: loop

- Entry: `loop` (the expression walked), optional `name`, `form` (`for_each` default, `index`,
  `iterator`; `convert_loop`'s vocabulary), `type` (the element's type, when it cannot be
  inferred), `body` (a list, as `build`).
- Inferred: the element type (`LoopToStream.elementOf` for an `Iterable`, the component type
  for an array, `Map.Entry<K, V>` over `entrySet()` for a `Map`; a wildcard's bound, as
  `Extract.typeName` writes it); the name (`ConvertLoop.elementName`: `orders` gives `order`,
  `entries` gives `entry`, else `item`; `entry` for a map; `i`, then `j`, for an index); the
  bound of an index loop (`.length`, `.size()`, or the int itself); imports.
- Forms it does not write: `while (cond)` and a three-clause `for` with its own update have
  nothing to infer and are `surround` with a typed head today. `convert_loop` turns a
  generated for-each into the other statement forms and `loop_to_stream` into a pipeline, so
  the generator needs only the forms a model starts from.
- Not in this slice: gorefactor's `op` (`sum`, `count`, `filter`, `map`, `any`, `index by
  key`), which writes the accumulator too. Open question 5.
- Refused: not walkable (example 5); a raw type ("orders is a List with no type argument, so
  its elements' type is not known. Pass type.", `ConvertLoop`'s wording); a name taken
  (example 5); `form` `index` over an `Iterable` that is not a `List` ("has no index: use
  for_each"); an expression that does not resolve ("orders does not resolve at line 14 of
  ...: declare it in an earlier step, or pass type and it is declared as a stub", where the
  stub is the bare-name field stub `Stubs` already writes, typed `List<type>` by its
  enhanced-for rule).

### Slice 2: call and construction

- Entry: `call` (`name`, `receiver.name`, `Type.name`) or `new` (a type), `args` (expressions
  in order; names mostly), `into` (a name, or `return`), `type` (the declared type, when it
  should not be the result's).
- Inferred: the overload, by the type check of the written call; the result type for `into`;
  whether `into` declares or assigns (a variable of that name in scope is assigned, else a
  local is declared; the result says which); the diamond for a generic class when `type` gives
  the arguments; imports, static ones by the existing rule.
- Not inferred: the arguments. Filling them from same-named variables in scope is open
  question 6.
- Refused: the wrong number of arguments, listing the parameters by name (example 10; for
  several overloads, each one's list); `into` with a void method; a result type unknown
  because the method is a stub (example 10); `into` a name that is a field or parameter of
  another type ("total is a Money and Pricing.count() returns int"; today this is the gate's
  compile refusal, reworded); a checked exception the enclosing method neither catches nor
  declares ("Files.readString throws IOException, which Loader.load(Path) neither catches nor
  declares. In the same batch with check \"end\", follow this step with replace_statements
  with surround try on its line, or send change_signature with add_throws first.").
- Stubs: a call to a method or constructor nothing declares is written and stubbed as in any
  edit (parameters from the arguments' types and names); only the result type needs `type`.

### Slice 3: guard

- Entry: `guard` (a condition, typed) or `guard_null` (a name or expression), `exit`
  (`return`, `continue`, `break`, `throw`; for `guard_null` also `require`), `value` (what to
  return; for `throw`, the exception's type), `message` (an expression, for `throw` and
  `require`).
- Inferred: little, and the sketch should say so. The braces and layout; whether `return`
  needs a value (the enclosing method or lambda's result type); whether `continue` or `break`
  has a loop or switch to leave (`Effects.loopOf`); the exception's import; `java.util.Objects`.
  `guard_null` writes `x == null`; a guard for "not null" is `guard` with the condition typed.
- Refused: examples 14; a condition that is not boolean (the gate's error, reworded: "count is
  an int, not a condition"); `value` with `continue` or `break`; a `require` with a `value`.
- What it deliberately does not do: guess a return value, pick an exception type, or add a
  message. Each is behavior.
- Honest size: a guard saves almost no typing (section 5). Its case is the refusals (a
  `continue` with no loop, a missing return value, an undeclared checked exception) caught
  before the gate with a fix named, and uniform layout. If the owner wants the smallest
  first build, this slice is the one to leave out (open question 7).

### Slice 4: replace_parameter (a refactoring)

**What gorefactor means by the name is a different operation.** Its `docs/edits.md`:
"replace a parameter with a new one computed from it", the new argument computed at each call
from the old one (`qual types.Qualifier` becomes `write func(types.Type) string`), the body's
uses rewritten. The brief for this sketch describes Fowler's "replace parameter with query": a
parameter dropped because the method can compute it from the others. Both are below; open
question 3 asks which is wanted first.

**The query form (examples 15-19): `remove_parameter` with `value` an expression.**

- Arguments: as `remove_parameter` today. `value` is a field or literal (built, #315) or an
  expression over the method's other parameters, its fields, constants and literals.
- What is written: the parameter and its `@param` tag removed from the declaration, the
  argument from every call (`SignatureChange`, as now), and in the body either each read
  replaced (a name or a literal, as #315 does) or a local of the parameter's name and type
  declared first, `String customer = order.customer();`. The local keeps the body's text, runs
  the expression once, and `inline` removes it when the model wants that.
- What is checked, since the type check catches none of it:
  1. **Every call passes the value.** Each call's argument must be `value` with that call's
     other arguments in place of the parameters, compared structurally with names by binding.
     `Fold.Holes` is this matcher (a parameter matches any expression, the same one each
     time); whether it can be reused as it stands was not checked. A call that passes anything
     else is refused naming it (example 17). This is what makes it a refactoring: no call's
     value changes.
  2. **No effect is dropped, doubled or reordered.** An argument substituted into `value` must
     be effect-free when `value` uses it, since the call wrote it twice and the method now
     computes from one evaluation (example 18; `Introduce.perCall` refuses the mirror case).
     With a call in `value` (`order.customer()`), it moves from the argument list to the first
     statement of the body: every argument written after it at a call must be effect-free
     (`Signatures`' `pure`), or it would now run before (example 18).
  3. **The value means the same in the body.** Without a call, `value` must be
     `Effects.fixed()` over parameters the body never assigns (`Introduce.byParameters`, the
     rule `as=parameter` uses in the other direction). Each name in it must resolve in the
     method as it did at the calls: parameters by position, fields and constants by binding
     (`NameCheck`), a field the body hides written `this.x` (`Signatures.readOf`).
  4. **The family.** A method that something overrides or implements, a lambda that
     implements it, or a method reference to it is refused, as #315 does ("each declaration
     could need another value").
  5. **Exceptional behavior.** A value that may throw (a dereference, a division) threw at the
     call and now throws in the method, after the receiver was evaluated. Reported through
     `Exceptional`; refused under `preserve_exceptions`.
  6. A constructor whose `this(...)` or `super(...)` reads the parameter cannot take a local
     before it: refused, or the value written inline there (decide when built).
- Kind: `REFACTORING`. No stubs: a name its output leaves unresolved is its own mistake.
- Probes (`src/test/resources/probes`): one per rule 2 and 3 case, printing before and after.

**gorefactor's form: a new parameter computed from the old one.** Two existing tools compose
to its simple case: `extract_variable` `as=parameter` on the body's expression of the old
parameter (every call then passes that expression of its own argument), then
`remove_parameter` of the old one, now unread, in one batch. Two gaps stop the general case:
`as=parameter` takes one occurrence (`all_occurrences` is "not built yet") and refuses an
expression with a call. Closing those gaps is smaller than a `replace_parameter` tool and
serves `parameterize` too; a `replace_parameter` alias could then send the batch.

## 5. How it composes

A method built from a stub. `Ledger.totalFor(String)` is a stub a test declared; line 14 is its
`throw new UnsupportedOperationException("not implemented");`.

With `build`:

```json
{"steps": [
  {"tool": "add_declaration", "args": {"file": "shop/Ledger.java",
    "content": "private final Pricing pricing;"}},
  {"tool": "replace_statements", "args": {"file": "shop/Ledger.java", "start_line": 14,
    "end_line": 14, "build": [
      {"guard_null": "customer", "exit": "require"},
      {"call": "Money.zero", "into": "total"},
      {"loop": "orders", "body": [
        {"guard": "!order.customer().equals(customer)", "exit": "continue"},
        {"call": "pricing.priceOf", "args": ["order", "customer"], "into": "price"},
        {"call": "total.plus", "args": ["price"], "into": "total"}]},
      "return total;"]}}],
 "check": "end"}
```

With `content`, the second step is:

```json
{"tool": "replace_statements", "args": {"file": "shop/Ledger.java", "start_line": 14,
  "end_line": 14, "content": "Objects.requireNonNull(customer);\nMoney total = Money.zero();\nfor (Order order : orders) {\n  if (!order.customer().equals(customer)) {\n    continue;\n  }\n  Money price = pricing.priceOf(order, customer);\n  total = total.plus(price);\n}\nreturn total;"}}
```

Both write:

```java
Money totalFor(String customer) {
  Objects.requireNonNull(customer);
  Money total = Money.zero();
  for (Order order : orders) {
    if (!order.customer().equals(customer)) {
      continue;
    }
    Money price = pricing.priceOf(order, customer);
    total = total.plus(price);
  }
  return total;
}
```

Counted (compact JSON, both batches whole, the field step included):

| | Typed `content` | `build`, nested (a) | One tool per generator (c) |
|---|---|---|---|
| Characters in the batch | 494 | 547 | 1,006 |
| Java typed in the body | 247 characters, 10 lines | 47 characters (one condition, `return total;`) | 47 |
| Names given | | 11 names, 82 characters | 11, 82 |
| Types the model had to know | `Money` twice, `Order`, the import of `Objects` | none | none |
| Steps | 2 | 2 | 8 |

What this shows, plainly:

- **`build` does not send fewer characters.** For short statements the JSON around a name
  costs about what the Java syntax did. The claim that holds is narrower: typed Java falls
  from 247 characters to 47, and what is left is the condition and the return, the ideas.
- **What the model no longer needs is knowledge, not keystrokes:** the element type of
  `orders`, the result type of `priceOf` and `plus`, the import. If it would have spent an
  `inspect` to learn one, or a refused call on getting one wrong, that is the saving, and it is
  what section 8 measures. On code whose types the model already has in context, expect no
  saving.
- **One tool per generator costs twice the typing**, and a statement inside the loop needs an
  anchor that does not exist yet (the count assumes a `where: "inside"`). This is the strongest
  argument for (a) over (c) for building new code; (c)'s names remain useful as aliases for a
  single statement.
- The saving grows with type text (`Map.Entry<String, List<Order>>`: 70 characters of head
  against `{"loop":"byCustomer"}`, 21) and with call sites (`replace_parameter`: one call
  against an edit per call).

## 6. Open questions for the owner

1. **Where the generators live.** (a) `build` on the edit tools, gorefactor's names as
   unpublished aliases (recommended: no new tool, nests, half the characters of (c)); (b) one
   `build_statements` tool with a `kind` (one name to find, a union of arguments, 41 tools);
   (c) `create_loop`, `create_call`, `create_guard`, `replace_parameter` published (names that
   are the intent, 44 tools, a step per statement).
2. **How a model finds them.** (a) Under `JREFACTOR_SKELETON_ONLY`, typed `content` that is
   one statement a generator writes is refused, naming the `build` to send (recommended as the
   experiment: the only mechanism measured to work here, a turn per steer, and it widens an
   experiment flag); (b) nothing beyond the description and the argument's name, measured
   first (cheapest; three A/B sets say it will go unused); (c) an A/B arm that publishes the
   aliases as tools, to learn whether a name alone is found (costs runs, not design).
3. **What `replace_parameter` means here.** (a) The query form, as `remove_parameter` `value`
   (recommended first: it widens a built option, and its inverse `as=parameter` already has
   the rules); (b) gorefactor's form, by closing `as=parameter`'s two gaps and composing (its
   evidence is gorefactor's 13 `replace_text` steps; none in this repo's log yet); (c) both
   under a `replace_parameter` tool that routes, as `move` does (a tool for what two options
   do).
4. **Explicit types or `var`.** (a) Always explicit, as `extract_variable`, `convert_loop` and
   `extract_function` write (recommended: one rule, and the type in the text is what the next
   read shows); (b) `var` where the file already uses it (matches the surrounding code, one
   more inference to get wrong); (c) an argument (a layout-like choice handed to the model,
   against the 2026-11-07 decision's spirit).
5. **The loop: a shell, or gorefactor's `op` too.** (a) The shell with `body` (recommended
   first: small, and `loop_to_stream` already turns it into `sum`, `filter`, `map`); (b) `op`
   as gorefactor plans (`sum`, `count`, `filter`, `map`, `any`, `all`, index by key), writing
   the accumulator and its declaration, where its evidence is (a summing loop by hand, twice);
   more saved per call, and each `op` is its own golden set; (c) `op` writing the stream form
   directly (shortest Java, overlaps `loop_to_stream`).
6. **Arguments of a call.** (a) Always given in `args` (recommended first: nothing guessed);
   (b) left out, each parameter takes the one variable in scope of its name and type, refused
   when any has none or two (the stub constructor's "fits exactly one field" rule, applied to
   scope; saves the most typing on long calls, and is a guess that compiles when wrong only if
   two variables swap); (c) `args` by parameter name, `{"order": "o"}` (clearer for long lists,
   longer for short ones).
7. **Which slices first.** (a) `replace_parameter`'s query form, then call, then loop, then
   guard (recommended: a refactoring first, then by how much bindings add); (b) loop first, as
   gorefactor's evidence orders it; (c) leave the guard out until a friction entry asks: it
   infers least.

## 7. Increments

One branch and PR each. Each PR updates `docs/tool-contract.md` (the argument, its refusals),
the roadmap row it finishes (new rows after #140) and the session notes. Goldens are named as
the existing ones are (`<tool>_<option>_<case>`).

1. **`remove_parameter` `value` as an expression of the other parameters, constants and
   literals.** `Signatures.removeWithValue`; the parameter rules shared out of `Introduce`
   (`byParameters`, `assigned`, the substitution in `perCall`) into one place both use;
   `Fold.Holes` or a matcher like it; `Tools.REMOVE_PARAMETER`'s description and `value`.
   Goldens: `remove_parameter_value_expression`, `remove_parameter_value_expression_local`
   (read twice, so a local), `remove_parameter_value_refused_other_expression`,
   `remove_parameter_value_refused_argument_effect`, `remove_parameter_value_refused_local_name`,
   `remove_parameter_value_refused_overridden`. Probe: `remove_parameter_value_expression`.
2. **A call in the value, declared first in the body.** `Signatures`, `Exceptional` (the
   reported change), `NameCheck`. Goldens: `remove_parameter_value_call`,
   `remove_parameter_value_call_refused_later_argument`,
   `remove_parameter_value_call_refused_constructor`, `remove_parameter_value_call_exceptional`
   (the result's second part), `..._preserve_exceptions_refused`. Probe: effect order.
3. **`build` with `call` and `new`, and typed strings, on `insert_statements` and
   `replace_statements`.** New class `Build` (entries to text, the `var` draft and the parse);
   `Primitives.insertStatements`, `replaceStatements`; `Tools.INSERT_STATEMENTS`,
   `REPLACE_STATEMENTS` (`Arg.json`). Goldens: `insert_statements_build_call`,
   `insert_statements_build_call_into`, `insert_statements_build_call_assigns`,
   `insert_statements_build_new_type`, `insert_statements_build_call_stub_with_type`,
   `replace_statements_build_mixed`, `insert_statements_build_refused_arity`,
   `insert_statements_build_refused_void_into`, `insert_statements_build_refused_stub_type`,
   `insert_statements_build_refused_with_content`, `insert_statements_build_refused_unknown_key`.
4. **`loop` with `body`.** `Build`; `ConvertLoop.elementName` made reachable (to `Names`);
   `LoopToStream.elementOf`, `unused`. Goldens: `insert_statements_build_loop`,
   `..._build_loop_array`, `..._build_loop_map`, `..._build_loop_index`,
   `..._build_loop_body_uses_element`, `..._build_loop_nested`, `..._build_loop_refused_not_walkable`,
   `..._build_loop_refused_raw`, `..._build_loop_refused_name_taken`, `batch_build_method`
   (section 5's batch).
5. **`surround` takes a loop object.** `Surround.wrap`, `Primitives.replaceStatements`,
   `Tools.REPLACE_STATEMENTS`. Goldens: `replace_statements_surround_loop`,
   `replace_statements_surround_loop_refused_name_taken`.
6. **`guard` and `guard_null`.** `Build`; `Effects.loopOf`. Goldens:
   `insert_statements_build_guard_continue`, `..._guard_return_value`, `..._guard_throw`,
   `..._guard_null_require`, `..._guard_refused_needs_value`, `..._guard_refused_no_loop`,
   `..._guard_refused_primitive_null`, `..._guard_refused_checked_exception`.
7. **`build` on `replace_expression`** (a call or construction as an expression).
   `Primitives.replaceExpression`, `Tools.REPLACE_EXPRESSION`. Goldens:
   `replace_expression_build_call`, `replace_expression_build_new`,
   `replace_expression_build_refused_statement_entry`.
8. **The aliases** `create_loop`, `create_call`, `create_guard`, `replace_parameter`
   (`Tools.ALIASES`, `ARG_ALIASES`; unpublished, for `batch` and the CLI, and for gorefactor's
   contract). Goldens: `create_loop_alias`, `replace_parameter_alias`.
9. **The mechanism question 2 chooses.** For the refusal: `Generated.checkBuild`, called where
   `checkSurround` and `checkChanged` are, under `Session.skeletonOnly`. Goldens:
   `insert_statements_skeleton_only_refused_loop`, `..._refused_call_declaration`,
   `..._passes_other_statements`. For published aliases: a server flag and
   `eval-tasks/jrefactor-ab-server-generators`.
10. **The measurement** (section 8): `SessionReport`, a task set, and an A/B run, asked for
    before it is started.

Increments 1 and 2 do not depend on question 1; they can start once question 3 is answered.

## 8. How to measure it

- **Session log** (`SessionLog` records each call's `tool_input`; `SessionReport` already reads
  `content` and `new` for its large writes). Add to the report, per branch: edit steps by what
  carried the code (`content`/`new`, `build`, `surround`, `constructor`, `implement`,
  `test_for`); Java characters typed (`content`, `new`, `declaration`, and the strings inside
  `build`); characters of names in `build`. The review's 540 of 560 becomes a trend: the
  share of edit steps that typed every statement.
- **A/B harness** (gorefactor's `refactor-ab`, the arms under `eval-tasks/`). A task set whose
  work is statements inside existing methods, in code the prompt does not show whole, so the
  types are not already in context: a loop over a map field, a call chain with declared
  results, a parameter every call derives from another. Arms: mcp today; mcp with `build`;
  with `build` and the refusal; with the aliases published. Per run: passed, cost, turns,
  typed lines (counted as `ab-library-skeleton` did, `build` strings included), tool errors.
- **What would show generators replaced typing, and paid:** `build` is called without a
  refusal first; compile refusals for a wrong type or a missing import fall; `inspect` calls
  made only to read a type before an edit fall; cost per run is no higher than the mcp arm.
- **What would show they did not:** `build` unused without the refusal (as `surround` was); or
  used, with typed lines down and cost up, as the skeleton arm showed (425 against 409 lines,
  8% more cost). By CLAUDE.md's measure, tokens against editing by hand, that is a result
  against building further, whatever the typed share says.
- **`replace_parameter`** is measured as the refactorings are: a friction-log entry that would
  have been N `replace_text` or `replace_expression` steps becomes one call, and
  `RefactoringStressTest` on Commons Lang counts applied and refused by reason.
