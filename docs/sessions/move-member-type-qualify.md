# move qualifies member types of the old class (issue #667)

- **Item:** issue #667, roadmap 514, the owner's first of three move/rename bugs
- **Date:** 2026-11-15
- **Branch:** move-member-type-qualify

## Plan

Two halves. In `move` of a method, every simple name that binds to a member type of the source
class (or a superclass, as `reached` already decides for fields and methods) gets the declaring
class as a qualifier, one insert per name in `movedEdits`. In `Imports`, the JDK index keeps only
packages exported to every module, so a non-exported class is never a candidate. `NameCheck`
gets a marker for member types so a tool that does not qualify (inline) refuses instead of letting
the import fix pick another class.

## Notes

- read: `MoveMember` outline, `run`, `checkBody`, `movedEdits`, `reached`; `NameCheck` whole; `Imports` outline, `toImport`, `chosen`, `Index`, `jdk`.
- plan: goldens first: `move_member_static_member_type` (red: `import java.util.Stack;`, compiled), `..._signature` (red: refused, `jdk.vm` import), `imports_never_jdk_internal` (red: the same import).
- refusal: a batch section named `@@decl` with `"content": "@@decl"` in the JSON: sections are referenced as `"@decl"`. My mistake.
- refusal: `insert_statements` default is after the line; "Unreachable code" after `return out;`. Retried with `where: before` by `retry`.
- read: probed `inline` of the same shape in a scratch project: it imported `java.util.Stack` silently. The `NameCheck` marker refuses it now (golden `inline_function_refused_member_type`).
- Java written through: `batch` (insert_statements, add_declaration) x2, `replace_declaration`, `add_declaration`, `replace_text` x3 on NameCheck. No hand edit of Java.

## Measured

Not measured (no session-log hook here); 6 tool calls that wrote Java, 2 refused and fixed by one retry and one resend.

## Assessment

Through the tools. The two refusals were argument mistakes the refusals named.

## Follow-ups

- `inline` of a method naming a member type of its class refuses now; it could qualify as `move` does.
- `Imports.chosen` still takes the project's own class last (after the file's earlier imports, the package's imports and the packages rule), as #131 chose; the issue asked for the project's type to win. Left as decided; the owner may reopen.
