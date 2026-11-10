# Loop examples: what loop_to_stream and convert_loop should do

The examples the loop tools are built against (the user asked for them, 2026-11-10). Each is a
loop as written, the form wanted, and its status. Three come from the user's
`tychofreeman/darktable-exported-image-date-extractor`, `Main.java` at a436633, before its
commit ff6b0bf replaced them by hand (issue #294). Status: **done** (a golden case pins it),
**partly**, **planned**, or **refused** (by a rule in `convert-loop-sketch.md`).

## loop_to_stream

### 1. An effect per element (darktable, done)

```java
for (RenamePlan.Skipped skipped : plan.skipped()) {
    err.println("skipped " + skipped.file() + ": " + skipped.reason());
}
// wanted, and what loop_to_stream writes:
plan.skipped().forEach(skipped -> err.println("skipped " + skipped.file() + ": " + skipped.reason()));
```

### 2. A filter, then effects (done: `loop_to_stream_filter_for_each`)

```java
for (Order order : orders) {
  if (order.open()) {
    log.add(order.id());
    System.out.println(order.id());
  }
}
// wanted:
orders.stream().filter(order -> order.open()).forEach(order -> {
  log.add(order.id());
  System.out.println(order.id());
});
```

### 3. Map into a fresh list, only read afterwards (done: `loop_to_stream_to_list`)

```java
List<String> labels = new ArrayList<>();
for (Item item : items) {
  if (item.price() > 10) {
    String label = item.name() + ": " + item.price();
    labels.add(label.trim());
  }
}
// wanted:
List<String> labels = items.stream()
    .filter(item -> item.price() > 10)
    .map(item -> item.name() + ": " + item.price())
    .map(label -> label.trim())
    .toList();
```

### 4. The same, the list changed afterwards (done: `loop_to_stream_collect_when_changed`)

`.collect(Collectors.toList())` in place of `.toList()`, which is unmodifiable.

### 5. addAll (done: `loop_to_stream_flat_map`)

```java
List<String> all = new ArrayList<>();
for (Shelf shelf : shelves) {
  all.addAll(shelf.titles());
}
// wanted:
List<String> all = shelves.stream().flatMap(shelf -> shelf.titles().stream()).toList();
```

### 6. Each branch of an if adds (darktable, done: `loop_to_stream_branches_flat_map`)

```java
List<File> files = new ArrayList<>();
for (String path : paths) {
    File file = new File(path);
    if (file.isDirectory()) {
        files.addAll(imagesIn(file));
    } else {
        files.add(file);
    }
}
// by hand in ff6b0bf, after imagesIn became a Stream and filesFor was extracted:
List<File> files = paths.stream().map(File::new).flatMap(Main::filesFor).toList();
// wanted from loop_to_stream alone:
List<File> files = paths.stream()
    .map(path -> new File(path))
    .flatMap(file -> file.isDirectory() ? imagesIn(file).stream() : Stream.of(file))
    .toList();
```

loop_to_stream writes this in one call (2026-11-10), with `collect(Collectors.toList())` since
`files` is passed to `RenamePlan.of` afterwards, which may change it. An `if`/`else` whose
branches each end in one `add` or `addAll` to the list, or add nothing, is a `flatMap` of a
conditional (`Stream.of(x)` for an `add`, `Stream.empty()` for nothing; `else if` nests).
`convert_lambda` then gives the method references.

### 7. A flag that any failure clears (darktable, done: `loop_to_stream_flag`)

```java
int status = 0;
for (RenamePlan.Rename r : plan.renames()) {
    out.println(r.from() + " -> " + r.to().getName());
    if (rename) {
        try {
            Files.move(r.from().toPath(), r.to().toPath());
        } catch (IOException e) {
            err.println("could not rename " + r.from() + ": " + e);
            status = 1;
        }
    }
}
return status;
// by hand in ff6b0bf: extract_function of the body as carryOut, returning false for status = 1, then
boolean allDone = plan.renames().stream()
        .map(r -> carryOut(r, rename, out, err))
        .reduce(true, Boolean::logicalAnd);
return allDone ? 0 : 1;
```

Not `allMatch`: it stops at the first false, and the loop renames every file. The shape: a local
set to one constant on some passes and never read in the loop. loop_to_stream writes (2026-11-10):

```java
int status = plan.renames().stream()
        .map(r -> {
            boolean statusChanged = false;
            out.println(r.from() + " -> " + r.to().getName());
            if (rename) {
                try {
                    Files.move(r.from().toPath(), r.to().toPath());
                } catch (IOException e) {
                    err.println("could not rename " + r.from() + ": " + e);
                    statusChanged = true;
                }
            }
            return statusChanged;
        })
        .reduce(false, Boolean::logicalOr) ? 1 : 0;
```

`extract_function` of the lambda's body then gives the hand version's `carryOut`. A body that is
`if (p) flag = c;` maps to the condition: `map(x -> p).reduce(false, Boolean::logicalOr)`.

### 8. A sum (done: `loop_to_stream_sum`, `loop_to_stream_sum_long_from`)

```java
int total = 0;
for (Item item : items) {
  total += item.price();
}
// wanted:
int total = items.stream().mapToInt(item -> item.price()).sum();
```

`int` and `long` only: a `double` sum is refused (roadmap #135). Overflow wraps the same way in both.
A starting value other than 0 is added in front: `long weight = crate + items.stream()...sum();`.

### 9. A count (done: `loop_to_stream_count`)

```java
int open = 0;
for (Order order : orders) {
  if (order.open()) {
    open++;
  }
}
// wanted:
int open = (int) orders.stream().filter(order -> order.open()).count();
```

`count()` is a `long`: the cast keeps the local's type; a `long` local needs none.

### 10. Search with break (done: `loop_to_stream_any_match`, `loop_to_stream_all_match`)

```java
boolean late = false;
for (Order order : orders) {
  if (order.late()) {
    late = true;
    break;
  }
}
// wanted:
boolean late = orders.stream().anyMatch(order -> order.late());
```

The `break` is what makes `anyMatch`'s short-circuit right here, unlike example 7. A flag that
starts `true` and is set `false` is `allMatch` of the negated condition (`if (!o.paid())` gives
`allMatch(o -> o.paid())`). Not built: `findFirst` for a loop that keeps the first match, since
`findFirst` throws on a null element where the loop would not.

### 11. A map's entries (done: `loop_to_stream_map_entries`, `loop_to_stream_map_entries_block`)

```java
for (Map.Entry<String, Integer> e : stock.entrySet()) {
  System.out.println(e.getKey() + "=" + e.getValue());
}
// wanted:
stock.forEach((key, value) -> System.out.println(key + "=" + value));
```

### 12. Group into a map (done: `loop_to_stream_grouping`, `loop_to_stream_grouping_mapped`)

```java
Map<String, List<Order>> byCustomer = new HashMap<>();
for (Order order : orders) {
  byCustomer.computeIfAbsent(order.customer(), k -> new ArrayList<>()).add(order);
}
// wanted:
Map<String, List<Order>> byCustomer =
    orders.stream().collect(Collectors.groupingBy(order -> order.customer()));
```

`groupingBy` makes a `HashMap` of `ArrayList`s too, but that is not in its contract, so as for
lists: plain `groupingBy` when the map is only read afterwards, else
`groupingBy(key, HashMap::new, Collectors.toCollection(ArrayList::new))` (with `mapping` for a
value other than the element). `groupingBy` throws on a null key, which `computeIfAbsent` grouped:
the result reports it as a change to exceptional behavior, refused under `preserve_exceptions`.

## convert_loop

### 13. An index loop that only reads the element (planned: `for` to `for_each`)

```java
for (int i = 0; i < names.size(); i++) {
  System.out.println(names.get(i));
}
// wanted:
for (String name : names) {
  System.out.println(name);
}
```

Refused when `i` is used other than in `names.get(i)`, or the body changes `names`. The element's
name comes from the list's (`names` to `name`), or `item`.

### 14. An iterator loop (planned: `iterator` to `for_each`, and back)

```java
for (Iterator<String> it = names.iterator(); it.hasNext(); ) {
  String name = it.next();
  System.out.println(name);
}
// wanted:
for (String name : names) {
  System.out.println(name);
}
```

Refused when the body calls `it.remove()`; back the other way, to make room for one.

### 15. A for and a while (planned: both ways)

```java
for (int i = 0; i < 3; i++) {
  System.out.println(i);
}
// wanted:
int i = 0;
while (i < 3) {
  System.out.println(i);
  i++;
}
```

`i` now lives after the loop: wrap the two in a block, or not when nothing after uses the name.
A `continue` in the body is refused for now (roadmap #133).

### 16. A do-while (planned)

```java
do {
  line = reader.readLine();
} while (line != null && line.isBlank());
// to while: refused for now (roadmap #134).
```

From `while` to `do_while` the condition is read twice on the first pass
(`if (c) do { ... } while (c);`), so it is refused unless the condition only reads locals.
