---
order: 109
item: 116
status: Done
---

## Item

Skeleton-only creation, an experiment flag: a Java `create_file` with member declarations is refused, naming the constructive operations

## Status

Done (2026-11-05), local session: `Session.skeletonOnly` from `JREFACTOR_SKELETON_ONLY=1`, checked in `Edits.createFile`; goldens `create_file_skeleton_only_*`; record in `docs/sessions/skeleton-only.md`; the A/B arm waits for the user's yes

## Evidence

The first slice; off by default, an A/B arm turns it on
