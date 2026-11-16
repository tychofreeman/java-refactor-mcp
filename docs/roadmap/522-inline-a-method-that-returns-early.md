---
order: 512
item: 522
status: Open
---

## Item

`inline` of a method whose body returns before its last statement (`if (s.length() <= n) { return s; } return s.substring(s.length() - n);`), at a call that is a whole `return` or an assignment statement: refused here ("has a body that is empty or returns before its last statement"); gorefactor inlines it.

## Status

Open

## Evidence

The reference for `freight-tail` (roadmap #520) as gorefactor's is written (add `tail`, `fold`, `inline` with `all_calls`) is refused at the inline, with one call left, `return last(code, 4);`. The reference here goes through `remove_parameter` with `value`, `add_parameter`, `function_to_method` and `rename`. In gorefactor's run six of eight attempts planned the fold and inline form.
