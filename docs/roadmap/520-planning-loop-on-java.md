---
order: 510
item: 520
status: Open
---

## Item

The planning loop on Java: gorefactor's `eval-tasks/learning/loop.py` (a model plans a change as one batch from names alone, the tools carry it, hidden tests judge it; a by-hand arm answers with text edits) run against this server, to see whether Java shows the gap Go does. Steps: a hand-written fixture with two of the Go freight tasks' shapes, a language switch in the loop (in gorefactor's repo: the harness is shared), then both arms at 8 attempts a task.

## Status

Open (2026-11-15, the user): the fixture is built. `eval-tasks/testdata/freight` (about 400 lines in four packages, its own tests), `eval-tasks/derivations-freight.json` (`freight-tail`, `freight-routes`), hidden tests in `eval-tasks/testdata/freight-tail` and `freight-routes`, and a reference batch for each in `eval-tasks/learning/refs/`, validated: each hidden test fails on the fixture and passes after `bin/jrefactor -C <copy> batch @<ref>`. Left: the loop's language switch (the build of the tool, `./mvnw -q -o test` as the check, `.java` in its patterns), and the runs.

## Evidence

gorefactor's freight loop, sonnet, 8 attempts a task (2026-11-15): 31/32 passed for $3.26 with the tools against 32/32 for $8.52 by hand; freight-routes cost a fifth of by hand. The same run found a tool bug (`inline` dropped `all_calls`, go-refactor-mcp #650) because six attempts met one refusal. This repo's A/B runs let the model read freely, and came out level with by hand; none plans from names. Writing the references here already found three gaps (#521 and #522, both fixed the same day; and the friction log of 2026-11-15, loop-freight-fixture).
