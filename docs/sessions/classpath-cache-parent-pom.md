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

