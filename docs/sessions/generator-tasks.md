# A task set for the generators

- **Item:** issue #376 (the generators sketch's increment 10, `docs/sessions/generators-sketch.md`
  section 8): the task set, before the A/B run. The user, 2026-11-13: build the task set and the
  arms first, then ask before the paid run. The report's counts were #403 (roadmap row 161).
- **Date:** 2026-11-13
- **Branch:** generator-tasks

## Plan

A fixture, `eval-tasks/testdata/depot` (packages `depot.stock` and `depot.orders`, 17 classes),
and three tasks in `eval-tasks/depot.json`. Each task's work is statements inside a method that
exists, in code the prompt names but does not show, so the types are not already in context:
the three shapes section 8 asks for.

| Task | What it asks | What fits |
|---|---|---|
| depot-reorders | `Inventory.reorders()`: a `ReorderLine` for each SKU whose available stock is below its reorder point, in insertion order, with the catalog's supplier | `build`: a `loop` over the map field `stock` (its entries), a `guard`, a `call` and a `new` |
| depot-invoice | `Invoicing.invoiceFor(order)`, which throws: the order's id, its customer's account number and billing address, total plus `Shipping.cost` | `build`: a call chain with declared results (`order.customer().account()`), a `new` |
| depot-shipping | `Shipping.cost(order, region)` loses `region`: every call passes `order.customer().region()` | `remove_parameter` with `value` (`replace_parameter`) |

Each hidden test (`eval-tasks/testdata/depot-<task>`) fails on the fixture and passes with a
reference solution; the fixture's own tests pass (scratch copies, JDK 21, `./mvnw -q -o test`,
as the harness runs it). The tools do two of the three in one call each, checked in scratch
copies: `remove_parameter` `value=order.customer().region()` (one declaration, three calls, the
test's included, with the reported exceptional change), and `insert_statements` with a `loop`
over `stock` whose body is a `guard`, a `call`, a `new` and `lines.add` (the entry's type read as
`Map.Entry<Sku, Stock>`). The invoice task is a chain of `call` entries.

## Notes

- The fixture's Java and the tests through `bin/jrefactor` batches (`create_file`, outside this
  repo's source roots, so written as text); its Maven files copied from the library fixture.
- One expected value was wrong as first written (a quote of 1,001 g at the EU rate is 28.00, two
  started kilos, not 19.00); found by checking the arithmetic before the run, fixed with
  `replace_text`.
- The arms are the next items: `JREFACTOR_NO_BUILD` (mcp without `build`) and
  `JREFACTOR_PUBLISH_ALIASES` (gorefactor's names as tools), each with a wrapper beside
  `eval-tasks/jrefactor-ab-server`.