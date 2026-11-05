# bin/jrefactor @file names its tool

- **Item:** the friction-log entry of 2026-11-05 ("bin/jrefactor @file takes the
  arguments only"), from #171's fold batch. The user's request of 2026-11-05.
- **Date:** 2026-11-05
- **Branch:** cli-tool-key

## Decisions (this session)

- A JSON object given alone (`@file`, `-`, `'{...}'`) names its tool with a
  `"tool"` key, as a batch step does; without one it is refused, saying so.
- After a tool's name, a `"tool"` key that names the same tool (by its name or a
  client's, through `Tools.named`) is dropped; one that names another is refused.
- `key_file` expansion is its own step (`withFiles`), so both forms get it.

## Notes

- `CliTest.aJsonObjectAloneNamesItsTool` red first ("No tool @..."), then green.
- `refusal:` my first batch's usage-text step had the text block's indentation
  wrong (8 spaces for 6); resent.
- `hand:` none.

## Measured

All Java through jrefactor (`replace_text`, `replace_declaration`,
`add_declaration`).
