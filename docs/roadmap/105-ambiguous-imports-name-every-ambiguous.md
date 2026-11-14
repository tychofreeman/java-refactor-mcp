---
order: 98
item: 105
status: Done
---

## Item

Ambiguous imports: name every ambiguous simple name in one refusal; choose, without refusing, the candidate the file imported before the call or batch, or the one in a package the file's other imports come from; otherwise suggest `java.*` first, not the last alphabetically; say in the result which was chosen

## Status

Done (2026-11-05), local session: `Imports.chosen`, the refusal collected in `Imports.toImport`; goldens `imports_chosen_by_package`, `imports_chosen_prior_in_batch`, `imports_refused_every_name`; record in `docs/sessions/ambiguous-imports.md`

## Evidence

Friction triage (2026-11-05, `docs/sessions/friction-triage.md`): six entries, 2026-11-02 to 11-04 (the suggested candidate, one name per refusal, the `org.eclipse.jdt.core.dom` family, `Path` refused after its import was removed, JUnit's `MethodInvocation` suggested, a batch step removing an import a later step uses); `Imports.toImport` refuses at the first name and suggests the last
