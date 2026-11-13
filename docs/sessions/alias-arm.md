# The A/B arm with the aliases published

- **Item:** issue #376's fourth arm: gorefactor's names for the generators as tools of their own,
  to answer the generators sketch's question 2c (is an option or a published tool found more
  readily). The user, 2026-11-13: build it now, as a server flag.
- **Date:** 2026-11-13
- **Branch:** alias-arm

## Plan

- `Session.publishAliases` from `JREFACTOR_PUBLISH_ALIASES=1`.
- `Tools.published` appends `Tools.PUBLISHABLE_ALIASES` (`create_loop`, `create_call`,
  `create_guard`, `replace_parameter`) after the tools, with their descriptions as they are; with
  `JREFACTOR_NO_BUILD` as well, only `replace_parameter`, since the others are `build`.
- Lookup by name needs no change: `Tools.named` already resolves the aliases.
- The wrapper `eval-tasks/jrefactor-ab-server-aliases`.
- `ToolsTest`'s limit of 50 counts `Tools.ALL`; the arm publishes four more, which the limit
  does not see.

Tests: `PublishedToolsTest` (the aliases after the tools; with both flags, `replace_parameter`
only).

## Notes

- A batch sent through an unquoted heredoc had the shell run its backticked names as commands
  (each failed; the batch was refused for another reason and changed nothing): the trap the
  session notes name. Sent again quoted.