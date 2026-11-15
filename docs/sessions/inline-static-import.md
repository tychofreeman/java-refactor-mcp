# inline through a single static import

- **Item:** issue #644, roadmap item 511
- **Date:** 2026-11-15
- **Branch:** inline-static-import

## Plan

The issue asks first whether `Imports.fix` can drop the import itself. It drops a type import
whose simple name the file used before the edit and not after, and skipped static imports
altogether. Give single static imports the same rule in `Imports.plan`: the inlined call was the
file's last use of `erased`, so the import goes, in that file only, and `Keys` is imported for
the inlined body as before. Red: the golden that pinned the refusal becomes an applied case.

## Notes

- read: `inspect` of `Imports.plan`, `Imports.names`, `Imports.staticImports`.
- Java edits: one `insert_statements` in `Imports.plan`; none by hand.
- The on-demand golden passes before and after: it pins that the change leaves `.*` alone. Its
  layout (`import p.lib.Keys;` before the static import) is the import fixer's as it was.
- No probe: removing an import whose name nothing uses changes no resolution, so nothing that runs.

## Measured

Not measured (no session-log hook in this session).

## Assessment

Through the tools. The rule is general, not inline's own: any edit that removes a file's last use
of a single static import now drops that import, as edits already did for type imports (a default,
recorded in the roadmap item; the full test suite pinned no case where it was kept).

## Follow-ups

None.
