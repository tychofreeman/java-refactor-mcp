# The classpath cache notices a change to a parent pom

- **Item:** order 64 (row 57): the classpath cache is keyed by the hash of
  `pom.xml` alone, so a change to a parent pom (a dependency version in
  `<dependencyManagement>` of the parent) leaves the cache stale. Assigned to the
  cloud session by the split on `main`.
- **Date:** 2026-11-04
- **Branch:** classpath-cache-parent-pom

## Plan

The change, at the highest level: the cache is valid while every pom the build
reads is unchanged, not only the project's own.

Operations, in order:

1. Find the key (`MavenBuild.pomHash`) and what it reads.
2. Red: a test with a parent pom in the parent directory (`<relativePath>`);
   a classpath is cached, the parent changes, and the next load runs Maven again
   (the cache is not used).
3. Green: the key covers `pom.xml` and every parent pom reached through
   `<parent><relativePath>` (default `../pom.xml`), recursively, each hashed in
   order. A parent that is not in the tree (from the local repository) is covered
   by its version in the child's `pom.xml`, as now.
4. The check: the existing `MavenBuild` tests and `./mvnw verify`.

## Notes

- `hand:` `MavenBuild.pomHash` (now hashing parents), `parentPom` (new), and the new
  `MavenBuildTest` were written by Python exact-string replacement, and the test file
  by `Write`. Tools that fit: `replace_declaration` for `pomHash`, `add_declaration`
  for `parentPom`, `create_file` for the test. Not tried: the jrefactor tools were not
  loaded in this session.
- `plan:` the key reads each parent with a regular expression, not a full XML parse;
  that is enough for `<relativePath>` in a pom, and a parse would need a dependency.
- `refusal:` none. The red test failed for the planned reason (the key did not change).

## Measured

Not measured: no session log for this branch. Counted from the notes: 2 hand edits
and 1 new test file, 0 refusals, 0 refactoring calls.

## Assessment

- **Through the tools or around them:** around for the edits (Python replacements and
  `Write`), though the tools for each exist.
- **Did the plan hold?** Yes: red first (the key did not change), then the key covers
  the parents.
- **One change that would have helped:** the session should load the jrefactor tools
  at the start, as in the other records.

## Follow-ups

- A parent named by a property (`<relativePath>${x}</relativePath>`) is read as the
  literal text, so it is not followed. Not reproduced; Maven resolves such a parent
  itself.
- A parent in the local repository (not in the tree) is covered by its version in the
  child's pom, as before.
