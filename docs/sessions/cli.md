# Command-line tools

- **Item:** the user's request (2026-11-03): another session runs in a
  container where the MCP server is not allowed, so its edits for orders
  24, 28, 32, 35, 37, 41 and 44 were all made by hand (friction log). It can
  run commands, so a command line like gorefactor's (`cmd/refactor`) lets it
  use the tools.
- **Date:** 2026-11-03
- **Branch:** cli
- **Shape:** gorefactor's CLI, since this project follows its standards:
  `list`, `-C dir`, a tool with `key=value` pairs, JSON, `@file` or `-`
  (stdin); `X_file=path` gives a text argument `X` from a file; exit status
  0 done, 2 refused, 1 error; the undo stack kept between invocations, one
  file per project in the user's cache. gorefactor's `-n` (preview) and
  `check` are left out until asked for.

## Plan

The change, at the highest level: every tool can be called once per
process from a shell, with the same definitions, arguments and results as
the server, and `undo` reaches calls made by earlier processes.

Derivation, in order:

1. Red: `CliTest`, against a temporary project: a `rename` by
   `key=value` changes the file and exits 0; `undo` in a second run puts it
   back; a third `undo` exits 2 ("Nothing to undo"). A JSON argument and an
   `@file` argument; `content_file`; a refusal exits 2 with the refusal
   text; `list` names every tool. `Cli.run` is a stub until step 2.
2. Green: `Cli`: parse flags and arguments (`key=value` typed by the
   tool's schema: integer, boolean, array), find the project root (the
   nearest directory with `pom.xml` at or above `-C`, else `-C`), load the
   undo stack into the project's `Session`, `Tools.call`, print `render()`,
   save the stack.
3. Green: `Journal.load` and `Journal.save`: the stack as JSON (each
   entry's name and each file's text before and after, null for none),
   keeping the newest 20.
4. `bin/jrefactor`: builds the jar when stale, as `bin/jrefactor-mcp`
   does, and runs `Cli`. Docs: CLAUDE.md (how a session without MCP uses
   it), README, the tool contract.

## Notes

- bulk: `CliTest` (115 lines), new; it declared the stub `Cli.run`.
- read: `Journal`, `Session`, `Tools.call`, `Args`' accessors, `Server.main`
  and `answer`, gorefactor's README and `cmd/refactor` usage line: to match
  the server's handling and gorefactor's command line.
- bulk: `Journal.save`/`load` (52 lines) and `Cli` (207 lines, by
  `create_file` over the stub): new behavior.
- hand: `bin/jrefactor`, made from `bin/jrefactor-mcp` by `sed` (a shell
  script; `replace_text` on a copy would have needed a copy tool first).
- probe: on this repo, `bin/jrefactor rename` of a local in `Cli`, then
  `bin/jrefactor -C src/main undo` in another run restored it, and a second
  `undo` exited 2. About 9 s per run.
- plan: steps 1-4 held, with step 3 (the journal) written before step 2's
  `Cli`, which needs it.

## Measured

`bin/session-report` (`.claude/session-log/cli.jsonl`), before this
section was written:

- tool calls: 16
- through jrefactor: 0 refactoring steps, 3 edit steps
- refused by jrefactor: 0
- by hand: 3
  - Bash: cd /home/cwfreeman/dev/ai-playground/java-refactor-mcp && cat > docs/sessions/cli.md <<'EOF'
  - Bash: sed -e 's/^# Starts the java-refactor-mcp server built from this checkout, serving the$/# Runs one j...
  - Bash: python3 - <<'EOF'
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 3
  - create_file src/test/java/io/github/tychofreeman/jrefactor/CliTest.java: 115 lines
  - add_declaration src/main/java/io/github/tychofreeman/jrefactor/Journal.java: 52 lines
  - create_file src/main/java/io/github/tychofreeman/jrefactor/Cli.java: 207 lines, replacing the file
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: git fetch -q; git merge -q origin/main 2>&1 | tail -2; git diff --name-only --diff-filter=U; R=$(bin...
- reads: Bash java 4

## Assessment

- **Through the tools or around them:** all Java through java-refactor-mcp;
  by hand were `bin/jrefactor` (a shell script, by `sed` from the server's)
  and the docs.
- **Large writes:** the test, `Cli` and the journal's save and load: all new.
- **Did the plan hold?** Yes, with the journal before `Cli`.
- **Reading:** the server's call path, to give the command line the same
  behavior; gorefactor's usage, to give it the same shape.
- **One change that would have helped:** none new; the session-notes entry
  for this is what the other session needs.

## Follow-ups

- About 9 s per run here: keep the Maven classpath (keyed by `pom.xml`'s
  text) and the last type check's count in the cache too.
- `key_file` inside a batch's steps; gorefactor's `-n` preview and
  `check`.
- "Nothing to undo in this session" reads oddly from the command line.
