---
order: 97
item: 104
status: Done
---

## Item

Write guard, an experiment: a hook that sends changes to `.java` files through the tools

## Status

Done (2026-11-05, the user: "experiment with a write guard"), as gorefactor's `cmd/write-guard`: `bin/write-guard`, `Guard` (`write`); Edit, Write and MultiEdit of a `.java` file, and sed -i, perl -i, tee, cp, mv, rm or a redirection into one, are denied; `./mvnw spotless:apply`, git and `resources/` allowed

## Evidence

What to learn: how many writes it refuses and whether each then went through a tool (`bin/session-report` counts denied calls), and its cost in turns; gorefactor's roadmap 53
