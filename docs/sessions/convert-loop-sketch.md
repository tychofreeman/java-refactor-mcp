# convert_loop: a sketch

Status: the user answered the questions on 2026-11-10 ("Decisions" at the end); `loop_to_stream`
is built first. Asked first (the user, 2026-11-10: "start building ideas around a tool to convert
different types of loops", including `while`, `for`, `do`-`while` and the functional forms). It
widens roadmap row #132 (a loop to a stream, roadmap only). The evidence so far is one friction
entry, issue #294; CLAUDE.md asks for evidence before a tool, so this note is for choosing the
shape, not a decision to build.

## What it does

One refactoring, `convert_loop`, rewrites a loop as another form that runs the same code in the
same order, or refuses and says why. It is to loops what `convert_lambda` is to lambdas,
method references and anonymous classes: one tool, the form chosen by `to`.

Arguments: `file`, `line` (the line the loop or the statement holding the pipeline starts on),
`column` when two start there, `to`, and `preserve_exceptions` as every refactoring.

`to`:

| `to` | Java form |
|---|---|
| `for` | `for (init; cond; update)` |
| `for_each` | `for (T x : source)` |
| `while` | `while (cond)` |
| `do_while` | `do { } while (cond);` |
| `iterator` | `for (Iterator<T> it = s.iterator(); it.hasNext();) { T x = it.next(); ... }` |
| `forEach` | `source.forEach(x -> ...)`, `map.forEach((k, v) -> ...)` |
| `stream` | `source.stream()....` (`map`, `filter`, `flatMap`, then `forEach`, `toList`, `collect`, `sum`, `count`, `anyMatch`, `allMatch`, `noneMatch`), `IntStream.range` for an index loop |

Example: `for (int i = 0; i < items.size(); i++) { total += items.get(i).price(); }` with
`to=for_each` becomes `for (Item item : items) { total += item.price(); }`; with `to=stream`,
`total += items.stream().mapToInt(Item::price).sum();` when `total` is an `int` and nothing
else in the body runs.

## How it works: recognize, model, render

Each form is parsed into one model, and each target is written from it, so N forms need N
readers and N writers, not N² rewrites.

```
record LoopShape(
    Source source,          // what it walks
    String element,         // the element's name (x), or null for a condition loop
    List<Statement> body,   // the body's statements, element access already replaced by element
    Exits exits,            // breaks, continues, returns, labels (Effects.leaves)
    Accumulator result)     // a recognized pattern of the body, or null: list add, sum, count, any
sealed interface Source {
  record Range(String index, Expression from, Expression to, int step) {}   // for (int i = a; i < b; i++)
  record Elements(Expression of, ITypeBinding type) {}  // an array, an Iterable, a Map's entrySet()
  record Iterated(Expression iterator) {}               // it.hasNext() / it.next()
  record Condition(Expression cond, List<Expression> init, List<Expression> update, boolean bodyFirst) {}
}
```

- **Readers.** A `Range` is read only when its index is read nowhere but as `a[i]` or `list.get(i)`
  and written nowhere but in the update; then it is also `Elements` of `a` or `list`. An
  `Iterated` loop is `Elements` when `it` is used only by `hasNext()` and one `next()` at the top.
  A stream pipeline is read back into the same model (`stream` to `for_each`).
- **Writers** take only the shapes they can write: `for_each` needs `Elements`; `forEach` and
  `stream` need `Elements` and the lambda rules below; `while` and `for` take anything.
- **Where it goes:** `ConvertLoop` (the tool, from `Opened`, its checks as `Precondition`s) and
  `LoopShape` (read and write). It uses `Effects` (`of`, `written`, `changesNow`, `leaves`,
  `loopOf`), `NameCheck` for every name whose scope changes, `Exceptional` for the reported
  changes, and `Edits.gated`: one text edit replacing the loop statement (or the declaration and
  the loop, for an accumulator).

## What must stay the same, and the rule for each

The type check catches little here; most hazards compile. Each needs a rule and a probe.

| Hazard | Where | Rule |
|---|---|---|
| `continue` in a `for` skips to the update | `for` to `while` | write the update before each `continue`, or refuse (question 2) |
| `break`, labeled `continue`, `return` from the method | to `forEach` or `stream` | refuse; an unlabeled `continue` becomes `return;` in a `forEach` lambda |
| `break` after setting a flag | to `stream` | the `anyMatch` / `allMatch` / `findFirst` accumulators; any other `break` refuses |
| A local declared outside and written in the body | to a lambda | refuse unless it is the recognized accumulator |
| A checked exception thrown in the body | to a lambda | refuse |
| The index or element variable written in the body | to `for_each`, from `Range` | refuse |
| The collection changed in the body (`add`, `remove`) | index to `for_each`, to `iterator`, to a lambda | refuse: an index loop allows it, an iterator throws `ConcurrentModificationException`; `it.remove()` keeps the loop an `iterator` |
| The condition or update has an effect, or is read twice | `while` to `do_while` (guarded: `if (c) do ... while (c)`), to `for` | refuse when the condition is not `Effects.fixed()` |
| The body runs once before the condition | `do_while` to `while` | question 3 |
| A loop variable's scope widens (`for` init to before a `while`) | to `while` | wrap in a block, or keep it unwrapped only when `NameCheck` finds no clash and nothing later uses the name |
| A floating-point sum | to `stream` `sum()` | `DoubleStream.sum` compensates rounding, so the result can differ: question 4 |
| `toList()` is unmodifiable | to `stream`, a list built by `add` | `toList()` when the list is only read afterwards, else `collect(Collectors.toList())` (the user, 2026-11-10) |
| `list.get(i)` on a `LinkedList` | `for_each` to `for` | allowed; the result notes that `get` is linear for a list that is not `RandomAccess` |
| Exceptions: an index loop past the end, an iterator after a change | between index and element forms | report through `Exceptional.change`; refuse under `preserve_exceptions` |
| Order of effects across stages | to `stream` | a sequential pipeline runs each element through every stage before the next, as the loop did; never write `parallel()`, `sorted()` or `distinct()` |

## Tests

- Goldens per pair and per refusal (`convert_loop_<from>_to_<to>`, `convert_loop_refused_<why>`).
- A probe per hazard row (`ProbeTest`): the program printed before and after. Here they matter
  more than usual, since most of the hazards compile.
- `RefactoringStressTest` on Commons Lang: every loop, every `to`, counting applied and refused
  by pair, which also measures how many real loops each reader recognizes.

## Increments, one PR each

1. Index `for` and `iterator` to and from `for_each`: the most common conversion, and the
   model's `Range` and `Elements` readers.
2. `for` and `while`, both ways.
3. `for_each` to and from `forEach` (and a `Map`'s `entrySet()` loop to `map.forEach`).
4. `for_each` to `stream` for #294's shapes: `forEach`, `map` + `toList` or `collect`, `filter`,
   `flatMap`, then the sums, `count` and the matches.
5. `stream` back to `for_each`.
6. `do_while`.

## Parity

The name and the `to` vocabulary go in `docs/tool-contract.md` first, for gorefactor to share.
Go has one `for`: three-clause, condition-only, and `range`, so `for`, `while` (condition-only)
and `for_each` (`range`) apply, and no functional forms. TypeScript has all of Java's, plus
`for...in`, with `forEach`, `map`, `filter`, `reduce` and `some`/`every` for `stream`.

## Questions for the user

1. **One tool or two?** (a) `convert_loop` with every `to`, as above; (b) `convert_loop` for the
   statement forms and `convert_to_stream` for the functional ones, whose rules differ most;
   (c) options on existing tools (none fits closely: `convert_lambda` is per expression).
2. **`continue` in a `for` going to a `while`:** copy the update before each `continue`, or
   refuse when there is one.
3. **`do_while` to `while`:** `while (true) { body; if (!cond) break; }`, the body copied
   before the loop, or refuse.
4. **A floating-point sum to a stream:** refuse, report the possible change, or write
   `reduce(0.0, Double::sum)`, which adds in the loop's order.
5. **Increment order:** start with 1 (index to element, common), or with 4 (the case #294 hit).

## Decisions (the user, 2026-11-10)

1. **Two tools**, since that is easier to use: `convert_loop` for the statement forms (`for`,
   `for_each`, `while`, `do_while`, `iterator`) and `loop_to_stream` for the functional ones
   (`forEach`, the stream pipeline), over one tool with every `to`. The way back, a stream to a
   loop, is a later increment.
2. **`continue` in a `for` going to a `while`:** refuse for now; copying the update before each
   `continue` is roadmap #133.
3. **`do_while` to `while`:** refuse for now; a rewrite is roadmap #134.
4. **A floating-point sum to a stream:** refuse for now; the options (report the change, or
   `reduce(0.0, Double::sum)` in the loop's order) are roadmap #135.
5. **Order:** `loop_to_stream` first (the case #294 hit), then the statement forms.