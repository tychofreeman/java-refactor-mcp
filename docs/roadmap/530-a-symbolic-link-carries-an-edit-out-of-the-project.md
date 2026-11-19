---
order: 520
item: 530
status: Done
---

## Item

An edit through a symbolic link writes outside the project: with `link.txt` linked to a file outside the root, `replace_text file=link.txt` changed that file. The path checks compared text only (`SourceProject.file`'s `normalize().startsWith(root)`). Refuse a path that a link carries out of the root, as gorefactor's #660 does.

## Status

Done (2026-11-19): `SourceProject.outside` resolves a path's links (for a new file, the nearest existing directory above it; a link pointing nowhere by its text) and compares with the real root; `SourceProject.file`, which every tool's path goes through, and `Journal.put`, the one writer, refuse such a path (`requireInside`). Tests in `JournalTest`.

## Evidence

Reproduced 2026-11-19 with `bin/jrefactor -C <dir> replace_text file=link.txt old=hi new=changed`, while comparing this repo with gorefactor's recent PRs (gorefactor issue #640, fixed there by #660).
