# The read guard: no false refusals of grep -c and hidden paths

- **Item:** the read guard (#185) refused five of this session's commands. Three were
  `grep` printing lines of Java, which the rule means to send to `inspect`; two were
  false: a `grep -c` (a count, no text), and a `cat` through a variable path into
  `resources/`. The user's choice of 2026-11-05: fix only the false ones
  (`docs/design.md`, Decisions, with the options it beat).
- **Date:** 2026-11-05
- **Branch:** read-guard-false-refusals

## Decisions (this session)

- A `grep`, `egrep`, `fgrep` or `rg` with a flag that prints no text of the files is
  not a read: `-c`, `-l`, `-L` (not for `rg`, where it follows links), `-q`, alone or
  among short flags (`-rlq`), and `--count`, `--files-with-matches`,
  `--files-without-match`, `--quiet`, `--silent`.
- A path a shell variable hides, when it is not under `resources/`, is refused with
  its own reason: the variable hides where the path goes; write it out.
- A file under `resources/` after a `cd` was already allowed (a test now pins it); the
  earlier refusal came from how that long command was split, which the new message at
  least explains.

## Notes

- `GuardTest` red first on the two false cases, the `cd` case green from the start.
- `refusal:` my first batch had the wrong indentation for `reads` (6 spaces for 4);
  resent.
- `hand:` none.

## Measured

All Java through jrefactor (`replace_text`, `add_declaration`, `replace_declaration`).

## Follow-ups

- gorefactor's guard got the same change (its #559), the same day.
- Reverted the same day in both guards, by the user's decision: `inspect` gained
  `pattern` and `names` (#202, #203; gorefactor's #560) and a grep of source is
  refused again (`docs/sessions/read-guard-to-inspect.md`).
