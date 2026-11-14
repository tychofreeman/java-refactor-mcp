---
order: 342
item: 352
status: Done
---

## Item

Checked-exception analysis copied three times (`Extraction`, `Surround`, `LoopToStream`; issue #481, code review), and the copies disagreed: `surround=try` around `jobs.add(() -> Files.readString(p))` counted the lambda's `IOException` and wrote a catch javac rejects, and none counted a try-with-resources' `close()`, so `extract_function` of one wrote no `throws` and was refused as a compile error

## Status

Done 2026-11-14: `Exceptions` beside `Effects` (`move` of `Surround.checked`, `caught`, `thrown`): `uncaught(roots)` skips lambdas and anonymous and local classes and adds each resource's `close()` (by `Hierarchy.supertypes`), caught by the try's own catches or one around it; `caught` takes a resource as covered too. `Extraction.thrown`, `Surround.wrap` and `LoopToStream.checkedThrown` call it; their copies are deleted. Goldens `replace_statements_surround_try_lambda`, `extract_function_try_with_resources`, `extract_function_try_with_resources_caught`. Left: `ThrowsCallers`' own test (line 146), while another PR changes that file

## Evidence

Issue #481; `docs/sessions/fix-checked-exceptions.md`
