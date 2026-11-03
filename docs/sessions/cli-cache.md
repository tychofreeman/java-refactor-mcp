# Faster command-line runs

- **Item:** the follow-up of `docs/sessions/cli.md`: about 9 s per
  `bin/jrefactor` run on this repo, now that the other session works only
  through it. Chosen by the user (2026-11-03).
- **Date:** 2026-11-03
- **Branch:** cli-cache

## Plan

Measured first (three runs each, this repo): `list` 0.2 s; `inspect`
right after an edit 9.4 s; `rename` right after an `undo` 4.7 s. So:
about 5 s of a run after an edit to `src/main` is a jar rebuild, because
`bin/jrefactor` (copied from `bin/jrefactor-mcp`) rebuilds whenever a
source is newer than the jar, and the CLI edits those sources. About 2.2 s
of every run is loading the project (Maven's classpath, 3.8 s as a plain
`mvnw dependency:build-classpath`), and about 1 s is the type check before
an edit.

The change, at the highest level: a run pays only for what changed since
the last one: the tools are rebuilt when their committed code changes,
the classpath is read again when `pom.xml` changes, and the files are
type-checked again when one of them changed.

Derivation, in order:

1. `bin/jrefactor`: the jar is stale when missing, or older than the last
   commit that touched `pom.xml` or `src/main` (`git log -1`); with no git,
   as now. Uncommitted edits, the CLI's own included, do not rebuild: the
   tools are the build from the last commit, as a server session's are the
   build from when it started.
2. Red, green: the classpath is cached with the `pom.xml` text it came
   from, and used while that text is the same and every entry exists
   (`MavenBuild.classpath(root, cache)`; a unit test with a cache file
   written for a temporary project whose `pom.xml` Maven could not build).
3. Refactor while green: `TypeCheck.Counted` keeps each file's hash, not
   its text.
4. Red, green: `TypeCheck.save` and `load` keep the last count between runs,
   as the journal does; a test counts, saves, loads into a new project, and
   sees no parse.
5. Measure again.

## Notes

- hand-adjacent: `bin/jrefactor`'s rebuild rule through `replace_text` (a
  shell script, so not Java).
- refusal: `add_declaration` of the classpath test: I used `root` where the
  test class's field is `dir`. Resent with `dir`.
- refusal: then "load(Path) ... not applicable for (Path, Path)": stubs are
  not declared for a new overload of an existing method. Answered with a
  preparing step in one batch: the overload `load(Path, Path)` first
  (`load(Path)` delegating, no behavior change), then the test, which then
  got its stub `MavenBuild.remember` (friction log).
- bulk: `MavenBuild.classpath(root, cache)`, `remember`, `pomHash` (38 lines)
  and `TypeCheck.save`, `load`, `build` (48 lines): new behavior.
- refusal: a `replace_text` of a Javadoc line I had guessed rather than read;
  read the three lines and resent.
- rename: `undoDir` to `cacheDir` (parameter and method) with `rename`;
  `{@code undoDir}` in a Javadoc sentence was not a reference, changed with
  `replace_text`.
- plan: steps 1-5 held.

## Measured again

Three runs each, this repo, after the first run filled the caches:

| | before | after |
|---|---|---|
| `inspect` after an edit | 9.4 s | 0.26 s |
| `rename` of a local | 4.7 s | 2.35 s |
| `undo` | 9.3 s | 0.26 s |

The first run with an empty cache: 2.6 s (Maven's classpath). What is left
of an edit is the type check after it, which the gate needs.

## Measured

`bin/session-report` (`.claude/session-log/cli-cache.jsonl`), before this
section was written:

- tool calls: 35
- through jrefactor: 2 refactoring steps, 16 edit steps
- refused by jrefactor: 3
- by hand: 4
  - Bash: cat > docs/sessions/cli-cache.md <<'EOF'
  - Bash: ls src/test/java/io/github/tychofreeman/jrefactor/ | tr '\n' ' '; sed -i 's/^# it with no arguments)...
  - Bash: export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 && sed -n 48,52p src/main/java/io/github/tychofr...
  - Bash: python3 - <<'EOF'
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 3
  - add_declaration src/main/java/io/github/tychofreeman/jrefactor/MavenBuild.java: 39 lines
  - add_declaration src/test/java/io/github/tychofreeman/jrefactor/TypeCheckTest.java: 22 lines
  - add_declaration src/main/java/io/github/tychofreeman/jrefactor/TypeCheck.java: 48 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: R=$(bin/session-report 2>/dev/null | tail -30); cat >> docs/sessions/cli-cache.md <<EOF
- reads: Bash java 9, inspect 3

## Assessment

- **Through the tools or around them:** all Java through java-refactor-mcp,
  the renames by `rename`; the shell script through `replace_text`. By hand:
  the docs and this record.
- **Large writes:** the two caches' code, new.
- **Did the plan hold?** Yes. Measuring first moved the largest cost from
  where I expected (Maven) to the script's rebuild rule.
- **Reading:** `MavenBuild.classpath`, `TypeCheck.errors`, `Cli.run`, to
  put the caches beside them.
- **One change that would have helped:** stubs for a new overload (friction
  log, 2026-11-03).

## Follow-ups

- The type check after an edit (2 s here) could narrow to the files that
  depend on the edited ones (also #10's follow-up).
- The server could use the classpath cache at start (3 s each start).
- A `pom.xml` that inherits from a parent pom: a change to the parent does
  not invalidate the classpath cache (an entry that no longer exists does).
