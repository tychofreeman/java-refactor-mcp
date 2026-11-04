# Stress tests recover from a killed run

- **Item:** roadmap #61 (local session's list, 2026-11-03): stopping
  `NarrowCheckStressTest` left a Commons Math file edited; it was found by
  a `diff -r` against the sources jar.
- **Date:** 2026-11-04
- **Branch:** stress-restore

## Plan

The change, at the highest level: a stress run keeps, outside memory, the
text of every Java file it started from, so the next run can put the
project back before it starts.

1. Red: `StressSnapshotTest`: `take` a temporary project's files, change
   one and add a Java file, then `recover`: the change is undone, the new
   file gone, the snapshot deleted. `StressSnapshot` is a stub.
2. Green: `StressSnapshot.take(root, files)` writes
   `.jrefactor-stress-snapshot.json` in the root; `recover(root)` restores
   from it if present and deletes it; `done(root)` deletes it.
3. Both stress tests call `recover` at start, `take` after it, `done` at the
   end.

## Notes

- bulk: `StressSnapshotTest` (43 lines) and `StressSnapshot` (77), new.
- hand-adjacent: `create_file` of the test declared the stub
  `StressSnapshot` in `src/main`, where test helpers do not belong. A batch
  deleted its declaration there and created the real class in `src/test`;
  the file left with only its package line was removed by `rm`: no tool
  deletes a file (friction log).
- probe: a 200-edit run on the Commons Lang copy killed with `kill -9`
  left its snapshot; with a file changed and a new Java file added by hand
  (as a kill mid-edit leaves them), the next run printed "put back 2 files a
  killed run left changed", and the copy matched a fresh unpack.
- hand: my first kill used `pkill -f` with a pattern that matched the
  shell's own command line, which killed the shell; redone by process id.

## Measured

- tool calls: 12
- through jrefactor: 0 refactoring steps, 7 edit steps
- refused by jrefactor: 0
- by hand: 4
  - Bash: git checkout -q main && git pull -q --ff-only && git checkout -q -b stress-restore && cat > docs/ses...
  - Bash: cat src/main/java/io/github/tychofreeman/jrefactor/StressSnapshot.java && git rm -q --cached src/mai...
  - Bash: sed -i 's/aside from the Java files in the edit/&/' /dev/null; export JAVA_HOME=/usr/lib/jvm/java-21...
  - Bash: export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 S=/tmp/claude-1000/-home-cwfreeman-dev-ai-playgr...
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 2
  - create_file src/test/java/io/github/tychofreeman/jrefactor/StressSnapshotTest.java: 43 lines
  - create_file src/test/java/io/github/tychofreeman/jrefactor/StressSnapshot.java: 77 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: python3 - <<'EOF'

## Assessment

- **Through the tools or around them:** all Java through java-refactor-mcp
  but the deletion of an empty file (no tool).
- **Did the plan hold?** Yes.

## Follow-ups

- Stubs for a class a test file uses could go in the test tree when the
  calling file is a test (friction log).
