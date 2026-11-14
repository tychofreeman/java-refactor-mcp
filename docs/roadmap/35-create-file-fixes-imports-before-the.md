---
order: 28
item: 35
status: Done
---

## Item

`create_file` fixes imports before the gate, as every other edit does; a new file's unimported library type is now declared as a project class (`List.java`)

## Status

Done

## Evidence

Full code review 2026-11-03; reproduced by `create_file_java_imports`. `createFile` now goes through `write`, which fixes imports before the gate
