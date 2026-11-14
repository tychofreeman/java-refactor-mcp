# Darktable Image Date Extractor

Renames images darktable exported to the date they were taken (EXIF
DateTimeOriginal): `2024-12-30_23-59-58.png`. `Main` is the command line,
`RenamePlan` works out the renames, `Renamer` carries them out and tells a
`Report` (`TextReport`, or `JsonReport` with `--json`), `DateTaken` reads the
date, and `RawProfile` decodes the hex EXIF that older darktable versions wrote
into PNGs.

## How we work

TDD, in this order; refactor only while every test passes:
1. Green: make the change easy. If the change would be hard, refactor first,
   before writing the test, until it is small and local.
2. Red: write the next test and see it fail. If the test needs code that does
   not exist, it needs a compiling stub, so the build still runs and the test
   fails on its assertion; the jrefactor edit tools declare one for you.
3. Green: make it pass with the least code, even code that duplicates what is
   there.
4. Green: refactor, removing that duplication and shaping the code for the
   changes we will want next. Rerun the tests.

When a test is failing or does not compile, the next step is code that makes it
pass, not a refactoring. If you find you need one, undo the test (or set it
aside), refactor while green, and add the test again.

Typical refactorings for step 1: rename something to what it will mean, extract
the code you will change into its own method, add the parameter or field the new
behaviour will need, move a declaration to where the change belongs.

One branch and one PR per item, never commits to `master`. Before pushing, run
what CI runs, `./mvnw verify` (see "Build and test"), and merge your own PR once
CI is green. CI (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on JDK 21
for every push to `master` and every pull request. When the user says "do X, then Y", do them in order, each in its
own PR.

When a rule could reasonably go more than one way, set out two or three options
with what each costs, let the user choose, and record the choice with the
options it beat in this file.

Match the surrounding code: its comment density, naming and idiom. Doc comments
say what a thing is or does, plainly.

## Work as a derivation

Treat the code as a structure you reshape with operations, not text you rewrite.
Before changing anything, write the change down as a derivation: a list of
operations that keep behavior, ending in the one small edit that changes it.
Then send it, in one batch where you can. Use the operations you have; where
none does a step, make the edit as small as it can be.

Moves that work:

- **Merge near-duplicates.** Write the shared method with what differs as its
  parameters (add_declaration, or extract_function from one copy), then fold it:
  every copy becomes a call. A %s in a String.format of the method matches
  constant text in the copies, so the text that differs can be a parameter.
- **Change a signature: expand, migrate, contract.** For one parameter,
  add_parameter, remove_parameter, move_parameter and change_type do it in one
  step, every call and override included.
- **Name before you move.** rename, extract_variable and extract_function give
  the parts names; the later operations then address them by name, which stays
  valid after earlier steps.

## Using java-refactor-mcp

`.mcp.json` starts `bin/jrefactor-mcp`, which serves this project with the
release jar `bin/jrefactor-env` locates
(`~/.local/share/java-refactor-mcp/java-refactor-mcp.jar`, or `JREFACTOR_JAR`).
Use it for every refactoring and edit of Java it can do.

1. **Load every schema at once:** ToolSearch `+jrefactor` with `max_results` 50.
2. **Read Java through `inspect`,** not Read, cat or sed: every result is sent
   again on each later turn, so read only what you need. Inspect the file first
   (it shows the file without its method bodies), then only the line ranges you
   will change. Each line is the number, two spaces, then the code: do not count
   those two spaces as indentation in `old`. To read a declaration wherever it
   is, `inspect` with `names` (`["RenamePlan.of"]`); to find a line or a use,
   with `pattern` (a regular expression; without `file`, the whole project), not
   grep. Use Grep or Bash only for files that are not Java.
3. **Prefer a refactoring to an edit.** rename, extract_function, inline,
   move_parameter, add_parameter, move and the rest carry a change to every use,
   type-check it, and refuse one that would break the build or change what the
   code does.
4. **Write through the edits:** `create_file` for new files (tests, fixtures),
   `add_declaration` for new members, `replace_declaration` for a whole method,
   field or type by name (empty content deletes), `replace_statements` and
   `insert_statements` inside a method, `replace_text` for a few lines,
   literals, comments and non-Java files. Imports are added and removed for you.
   Write a test first with `create_file` (`test_for`) or `add_declaration`: what
   it calls that nothing declares gets a "not implemented" stub, and
   `add_declaration` of the real one replaces the stub.
5. **Plan from one read, send one batch.** Dependent steps (a signature, then
   its callers) go in one `batch` with check `"end"`; give lines as you read
   them before the batch. `undo` reverts the last call or batch.
6. **Read refusals; they say what to send.** A refusal changes nothing.

When no tool seems to cover a change, name what the result is made of in
operations (extract, fold, inline, move, ...) and look for a sequence of two or
three tools that composes to it. Only then make it by hand, as small as it can
be, and log it in `docs/friction-log.md`: what you needed, the sequences you
tried and why each failed. Append entries at the end of the file. The log is the
evidence for which java-refactor-mcp tool to build next.

**Without the MCP server:** `bin/jrefactor` runs the same tools from a shell,
one call per run, with the same arguments and results: `bin/jrefactor list`,
`bin/jrefactor rename file=... line=7 name=t new_name=total`, or a JSON object
(`'{...}'`, `@file`, or `-` for stdin). Exit status 0 done, 2 refused, 1 error.
`bin/jrefactor undo` reverts the last call.

## Build and test

```sh
./mvnw verify    # compile, run the tests, build the jar
./mvnw test      # tests only; -q hides the counts (see target/surefire-reports)
java -jar target/darktable-date-namer.jar [--rename] <image or directory>...
```

- JDK 21, a full JDK with `javac`. The JDK 25 on this machine is only a
  runtime and `JAVA_HOME` is unset, so set
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` or the build fails with
  "release version 21 not supported". Maven comes from the wrapper.
- `./mvnw package` builds `target/darktable-date-namer.jar` with its
  dependencies (maven-shade-plugin).

### Test fixtures

`src/test/resources` holds real darktable exports and variants of them, all
taken 2024-12-30 23:59:58: a date the week-based year `YYYY` gets wrong.

- `darktable-5.4-exif.png`: darktable 5 writes a PNG's EXIF as an `eXIf` chunk.
  Made with `darktable-cli src.jpg out.png --core --configdir <tmp> --library
  :memory: --conf plugins/lighttable/export/metadata_flags=ff`; without that
  flag a fresh config exports no metadata.
- `raw-profile-exif.png`, `raw-profile-exif-big-endian.png`: the `eXIf`
  payload moved into a zTXt "Raw profile type exif" chunk, as older darktable
  (through exiv2) wrote it: `\nexif\n%8d\n`, then the payload in hex lines of
  72, prefixed with `Exif\0\0`. exiftool cannot change the byte order inside a
  PNG; the big-endian payload came from a JPEG (`exiftool -ExifByteOrder=MM`,
  then `exiftool -b -EXIF`).
- `no-exif.png`: the export without its `eXIf` and XMP chunks.
- `darktable-5.4-subsec.png`: a darktable 5.4 export, made as above, of a 16x16
  JPEG given `SubSecTimeOriginal=123` and `OffsetTimeOriginal=+01:00` by
  exiftool; darktable keeps both.

## Decisions

- **Sub-seconds in the name** (2026-11-10): `2024-12-30_23-59-58.123.png`, the
  milliseconds of `SubSecTimeOriginal` after a dot, as ISO 8601 writes a
  fraction of a second; a file without it keeps `2024-12-30_23-59-58.png`, and
  `_2`, `_3` remain only for names that would still be the same. Beat:
  `-123` (one dot, but reads as part of the time or a counter), appended
  Pixel-style `58123` (runs the seconds and milliseconds together), and
  keeping `_2` ordered by sub-seconds (the name would not show the order).
- **JSON output** (2026-11-10): `--json` prints one object to stdout,
  `{"dryRun", "renamed": [{from, to}], "skipped": [{file, reason}], "failed":
  [{from, to, error}]}`, the same shape in a dry run (`renamed` then lists the
  renames that would be made). Beat: always JSON (harder to read at a terminal)
  and a separate `planned` key for a dry run (two shapes for a script).
  Written by hand, not with a JSON library: four lists of strings.

## Things not to do

- Do not commit generated files, `target/`, or IDE metadata.
