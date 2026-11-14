---
order: 42
item: 49
status: Done
---

## Item

Misleading names: `Extract` maps each variable to "declared inside", so `used.get(r)` reads as "used"; `Stubs` `implicit` is set as `!created.contains(...)`, the inverse of its name

## Status

Done

## Evidence

Full code review 2026-11-03: reading, not run. Done for `Extraction`: `Plan.declaredInside` is the set of variables declared inside, so `callOf` asks `declaredInside().contains(r)`. Open: `Stubs` `implicit` matches its use; why a class this call created gets no default-constructor stub is not in the code (a question). Record: `docs/sessions/misleading-names.md` Stubs' `implicit` is renamed `needsDefault` (`docs/sessions/stubs-implicit-name.md`)
