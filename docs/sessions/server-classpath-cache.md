# The server reads the classpath cache the command line keeps

- **Item:** roadmap order 63 (row 56): the server loads its project without
  the classpath cache, so each start runs Maven (about 3 s), while `bin/jrefactor`
  keeps the classpath between runs. Assigned to the cloud session by PR #79,
  which is not merged; taken from `main` because the fix has one obvious form
  (use the cache the command line already keeps).
- **Date:** 2026-11-04
- **Branch:** server-classpath-cache

## Plan

The change, at the highest level: a server start pays for Maven only when
`pom.xml` changed, the same as a command-line run.

Operations, in order:

1. Make the classpath cache file of a project a named function of the
   project root, shared by `Cli` and `Server` (`Cli`'s naming moves to one
   place; the file name stays the same, so existing caches still load).
2. `Server.main` calls `SourceProject.load(root, <that cache>)` instead of
   `SourceProject.load(root)`.
3. The check: a test that starts the project twice over a temporary
   project's cache and sees the second load run no Maven (the existing
   `MavenBuild.classpath(root, cache)` test shape). Red first.

The one behaviour change: a server start reads the user's cache directory. A
cache file of a different `pom.xml` is ignored, as it already is for the CLI.

## Notes

