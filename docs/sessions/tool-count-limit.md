# Tool-count limit (roadmap order 15)

**Item:** a test that holds the tool count, raised only with the user.
**Source:** gorefactor's tool budget (roadmap order 17 there). The user raised
the limit to 50 on 2026-11-04 so that ten higher-level tools can be added
(`docs/sessions/`, the split of `replace_text` work).
**Date:** 2026-11-04

## Plan

- Change: `ToolsTest` asserts `Tools.ALL` has at most 50 tools and that
  every name is unique. Raising the limit is a change to that one constant.
- Operations: none needed. The test reads `Tools.ALL` directly, as
  `ServerTest` reads the tool list through `Tools`.
- The one behavior-changing edit: the test itself. No tool is added here.

## Notes

- `hand:` `ToolsTest.java`, a new file of about 30 lines, written with
  `Write`. `create_file` covers it (its import fix and gate run on the new
  file); the server was not used because this session's tools are the
  build from before this branch, and a test file needs no refactoring.
- Limit 50, as the user asked on 2026-11-04.

## Assessment

Small and new; nothing refactorable. The test is the item's whole content.

## Follow-ups

- The ten higher-level tools, one PR each (see the session's plan).
