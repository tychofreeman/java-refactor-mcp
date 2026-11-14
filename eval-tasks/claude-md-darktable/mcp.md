# <project>

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

1. **Load every schema at once:** ToolSearch `+jrefactor` with `max_results` 50.
2. **Read Java through `inspect`,** not Read, cat or sed: every result is sent
   again on each later turn, so read only what you need. Inspect the file first
   (it shows the file without its method bodies), then only the line ranges you
   will change. Each line is the number, two spaces, then the code: do not count
   those two spaces as indentation in `old`. To read a declaration wherever it
   is, `inspect` with `names` (`["Cart.total"]`); to find a line or a use,
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

## Build and test

```sh
./mvnw -q -o test   # run the tests
```
