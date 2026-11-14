---
order: 180
item: 187
status: Done
---

## Item

A project with `module-info.java` was checked as the unnamed module, so a sealed type permitting a class of another package was an error before any edit, and a `rename` or `move` of its file was refused (issue #438, from the jdk-classfile-backport stress run)

## Status

Done 2026-11-13 (`TypeCheck.counts` drops `IProblem.SealedPermittedTypeOutsideOfPackage` when `SourceProject.namedModule`; the narrowed check counts from scratch when a `module-info.java` appears or goes; `FORMAT` 4, so saved counts are made again. A default to reopen, "Decided 2026-11-13: in a named module the gate does not count a sealed type's permitted class in another package" above; `requires`, `exports` and `create_file` of a `module-info.java` left as they were)

## Evidence

`docs/sessions/stress-enum-switch.md`; `docs/sessions/named-module.md`
