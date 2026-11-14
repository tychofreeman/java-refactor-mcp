---
order: 486
item: 496
status: Done
---

## Item

Required arguments were declared in each tool's schema (`.required()`) but never enforced centrally (issue #521, code review): `Tools.Def` kept only the argument names, `Args.check` refused unknown arguments only, and each tool checked its own required ones by hand, each in its own words ("invert_if needs line, the 1-based line the if starts on.", "extract_function needs start_line and end_line.", "replace_parameter needs value.")

## Status

Done 2026-11-14: `Tools.Def` keeps its `List<Arg>` (`arguments`, `args()` their names), and `Args.check` refuses a required argument that is absent or null, before the tool runs, in one wording from the argument's description (`Arg.missing`): "invert_if needs line: The 1-based line the if starts on. Pass line." The owner's option (a), over (b) a central check with per-tool wording through overrides and (c) leaving it (Decisions in `docs/design.md`). The hand-written checks in `Tools` (rename, extract_variable, inline_variable), `InvertIf`, `RemoveElse`, `MoveStatement`, `ConvertLoop`, `LoopToStream`, `TypeSwitch`, `ConvertLambda`, `Extract`, `Declarations.replace`, `Edits` (replace_text's `new`) and `Primitives.line` are gone; routers and route-dependent arguments keep theirs. `RequiredArgsTest` walks every tool and alias

## Evidence

Issue #521 (code review of 2026-11-13). `RequiredArgsTest` (every `Def`, each required argument left out); goldens `invert_if_refused_no_line`, `replace_parameter_alias_refused_no_value`
