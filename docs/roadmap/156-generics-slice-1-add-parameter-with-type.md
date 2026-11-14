---
order: 149
item: 156
status: Done
---

## Item

Generics, slice 1: `add_parameter` with `type_parameter` on a class, no members yet (increment 4 of `docs/sessions/generics-sketch.md`)

## Status

Done 2026-11-13 (`TypeParameters`: the class declares the parameter and every use in the project passes `value`, by the table in `docs/tool-contract.md`; a use in the class's own instance code passes the parameter; diamonds where a declared type fixes the target, checked by bindings after the write; a raw use left behind is refused; `CallTargets`. The user, 2026-11-13, each the sketch's recommendation: on `add_parameter`, over a new `add_type_parameter` tool and over `change_type`; `Box<T>` in instance code and `Box<String>` in static code, over a fallback per error and over always `Box<String>`; diamonds where a declared type fixes the target, over always the full form and over wherever inference agrees)

## Evidence

A review found generics a gap; the friction log has no entry asking for it
