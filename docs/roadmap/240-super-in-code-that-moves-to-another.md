---
order: 232
item: 240
status: Done
---

## Item

`super` in code that moves to another object was not refused: `convert_lambda` between an anonymous class and a lambda, and `type_switch_to_method` (`EnumSwitch` too) into a case's type, changed which superclass `super` meant; `NameCheck` passed, as both resolve to one declaration

## Status

Done 2026-11-13 (`ConvertLambda.refuseSelf` refuses an unqualified `SuperMethodInvocation`, `SuperFieldAccess` or `SuperMethodReference`; `TypeSwitch.usesOnly`, which `EnumSwitch` shares, refuses any. Probes `convert_lambda_anonymous_super`, `convert_lambda_lambda_super`, `type_switch_super`, `type_switch_enum_super`; goldens `convert_lambda_to_lambda_refused_super`, `convert_lambda_to_anonymous_refused_super_reference`, `type_switch_to_method_refused_super`). Default to reopen (the smallest fix): refuse, over rewriting `super.m()` as a call that keeps its target

## Evidence

Issue #472 (code review of 2026-11-13); `docs/sessions/fix-lambda-super.md`
