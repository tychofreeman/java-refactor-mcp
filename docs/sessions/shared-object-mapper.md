# One ObjectMapper for the session log and its report

- **Item:** order 34 (row 27): `SessionLog` and `SessionReport` each build their
  own Jackson `ObjectMapper`. Assigned to the cloud session by PR #79 (not
  merged); taken from `main`, since the fix has one obvious form.
- **Date:** 2026-11-04
- **Branch:** shared-object-mapper

## Plan

The change, at the highest level: the session log's JSON is read and written
through one mapper, so its settings are stated once.

Operations, in order:

1. Find the two mappers and what each configures.
2. If the settings are the same, one shared constant in `SessionLog` that
   `SessionReport` uses; if they differ, the difference is named and kept.
3. No behaviour change; the existing `SessionLogTest`/`SessionReportTest` are
   the check. No new test.

## Notes

