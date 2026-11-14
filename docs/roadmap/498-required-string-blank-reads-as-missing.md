---
order: 488
item: 498
status: Done
---

## Item

`Args.required` refused an empty string in its own words ("rename needs new_name."), not in the
wording #620 introduced for a missing argument (`Arg.missing`), and an empty or blank required
string passed `Args.check` and reached the tool, which refused it in its own words ("'  ' is not a
Java file in this project", "name is Type.method, ... not   ").

## Status

Done 2026-11-14: `Args` keeps the tool's `List<Arg>` (`Tools.call`; a router's target's through
`Args.routed(String, List<Arg>)`). `Args.check` refuses a required string sent empty or blank as
one left out, and `Args.required` refuses empty, blank or absent in `Arg.missing`'s words:
`rename` with `new_name=""` is "rename needs new_name: The new name; a valid Java name. Pass
new_name.". `content` and `new` may still be empty (#620's decision, `Args.MAY_BE_EMPTY`). The
central refusal of a blank value is a default to reopen (options in `docs/design.md`, Decisions).

## Evidence

Issue #622 (left open by #620, issue #521). `RequiredArgsTest.aRequiredStringSentEmptyOrBlankIsRefusedAsOneLeftOut`
(every tool, each required string argument but `content` and `new`, sent `""` and `"  "`),
`RequiredArgsTest.contentAndNewMayBeEmpty`, `ArgsTest.requiredRefusesAnEmptyValueInTheArgumentsWords`;
golden `rename_refused_empty_new_name`.
