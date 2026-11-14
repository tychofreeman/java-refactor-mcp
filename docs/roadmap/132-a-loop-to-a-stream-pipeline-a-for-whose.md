---
order: 125
item: 132
status: Done
---

## Item

A loop to a stream pipeline: a `for` whose only statement is an effect becomes `forEach`; `list.add(f(x))` into a fresh list `map(...)`; `if (p) add` `filter`; `addAll` `flatMap`. Refused for a loop that `break`s, writes a local declared outside it, or throws a checked exception. The collected list is `toList()` when it is only read afterwards, else `collect(Collectors.toList())`

## Status

Built (2026-11-10, after the user widened it, `docs/sessions/convert-loop-sketch.md`): `loop_to_stream` with `forEach`, `filter`, `map`, `flatMap`, `toList()` and `collect`, an if/else that adds as a conditional `flatMap`, flags (`reduce`), `int`/`long` sums and counts, `anyMatch`/`allMatch` for a flag set then `break`, `Map.forEach` for entries, and `groupingBy`: examples 1-12 of `docs/sessions/loop-examples.md`. Open: `findFirst`, arrays, and #135; the statement forms (examples 13-16) are `convert_loop` (#138, done, with #133 and #134). Earlier: roadmap only (the user, 2026-11-10, over building the tool now and over a first cut of `forEach` and `map` only), until the friction log shows it again; the collector choice is the user's (2026-11-10), over always `toList()` with a refusal when the list is changed and over always `collect`

## Evidence

Widened by the user on 2026-11-10 to every loop form: `docs/sessions/convert-loop-sketch.md` (`convert_loop`, a sketch with open questions). Issue #294 (2026-11-10, a downstream project's friction log): three loops to streams, two by hand with `replace_statements`, the third by `extract_function`, `change_type`, `remove_parameter` and `move_parameter` with the loop itself by hand; `extract_function` then `fold` does not compose to it, as `fold` matches a method's statements, not a loop's shape. Go has no streams; TypeScript's counterpart (a loop to `map`/`filter`) is not in gorefactor either
