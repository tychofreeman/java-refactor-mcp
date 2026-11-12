# Build and test on JDK 21 and 25

- **Item:** the user asked whether releases could be made for Java 21 and Java 25 (2026-11-11).
  The jar built for 21 already runs on 25 (started there by hand); what failed was building
  under 25, which disconnected the MCP server (`bin/jrefactor-mcp` builds with the default
  `java`, 25 on the user's machine). The user chose (2026-11-11): fix the build on 25, run CI on
  21 and 25, keep one release jar built for 21 and say it runs on 21 or later; over a jar per
  version, which would hold the same code and only stop running on 21.
- **Date:** 2026-11-11
- **Branch:** build-on-jdk25

## Plan

1. Fix what JDK 25's `javac` warns on that 21's does not (`-Xlint:all -Werror`): two doc comments
   attached to no declaration (`dangling-doc-comments`, new in JDK 23).
2. CI: `build-and-test` as a matrix over `21` and `25`, the 21 leg keeping its check name.
3. Release notes and the README: runs on Java 21 or later, built for 21, tested on 21 and 25.

## Notes

- `Cli.java`: `classpathCache` had been added between `cacheDir()` and its Javadoc, and
  `cacheDir()` had gained a one-line comment of its own; the fuller comment moved back onto it and
  the one-liner went. `InlineFunction.java`: an older Javadoc of `ParamUse` above the current one,
  dropped.
- refusal: a `replace_text` whose `old` I wrote from memory, not from `inspect`; read, then resent.
- Every edit through `bin/jrefactor` (the MCP server still disconnected).
- `./mvnw verify` on JDK 25: 725 tests, all pass (5 opt-in skipped), as on 21.

## Assessment

- **Through the tools or around them:** through them; no Java by hand.
- **One change that would have helped:** CI on 25 from the start; the warnings came in with
  ordinary edits and broke only machines whose default `java` is 25.

## Follow-ups

- When a JDK after 25 is the latest LTS, add it to the matrix.
