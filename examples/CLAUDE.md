# <project>

Work test first:
* Write the tests we need next first.
* Then make the tests pass with the minimal amount of code.
* Rerun the tests.

Make every change in two phases: "make the change easy, then make the easy change".

1. Make the change easy. Before changing any behaviour, refactor: change the design of the code, without changing what it does, until the change you need is small and local. Typical refactorings: rename something to what it will mean, extract the code you will change into its own method, add the parameter or field the new behaviour will need, move a declaration to where the change belongs. The tests keep passing after every refactoring.
2. Make the easy change. Then make the behaviour change itself, which should now touch little code.

Do the refactorings in phase 1 with the jrefactor MCP tools (rename, extract_function, extract_variable, inline, move_parameter, add_parameter, move, ...). Make the change in phase 2 with the jrefactor edit tools (replace_statements, insert_statements, replace_expression, add_declaration, replace_declaration, create_file, ...). Each tool type-checks its change and refuses one that would break the build or change what the code does; a refusal says what to try instead.

Read Java code with the jrefactor inspect tool, not with Read, cat or sed: every result stays in the conversation and is sent again on each later turn, so read only what you need. Inspect a file first: it shows the file without its method bodies, with line numbers. Then inspect only the methods or line ranges you will change. Each line is the number, two spaces, then the code. Use Grep or Bash for files that are not Java and to find where a name is used.

## Work as a derivation

Treat the code as a structure you reshape with operations, not text you rewrite. Before changing anything, write the change down as a derivation: a list of operations that keep behavior, ending in the one small edit that changes it. Then send it, in one batch where you can. Use the operations you have; where none does a step, make the edit as small as it can be.

Moves that work:

- **Merge near-duplicates.** Write the shared method with what differs as its parameters (add_declaration, or extract_function from one copy), then fold it: every copy becomes a call. A %s in a String.format of the method matches constant text in the copies, so the text that differs can be a parameter.
- **Change a signature: expand, migrate, contract.** For one parameter, add_parameter, remove_parameter, move_parameter and change_type do it in one step, every call and override included.
- **Name before you move.** rename, extract_variable and extract_function give the parts names; the later operations then address them by name, which stays valid after earlier steps.
