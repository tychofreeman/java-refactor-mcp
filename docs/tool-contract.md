# Tool contract

Status: draft, 2026-11-02. This is the contract the Java server should share with
gorefactor. It was written from gorefactor's status page, not from its code, so
check each point against `tychofreeman/go-refactor-mcp` (README "Status" and
"Roadmap", `CLAUDE.md`) before relying on it.

## Principles

- **Few tools, small results.** Each tool does one job. Results report what
  changed, not the whole file.
- **Refusals explain the next step.** A refusal names the reason and what to try
  instead, in plain words.
- **Do dependent work in one call.** `batch` runs several steps as one unit and
  rolls back as a whole.
- **1-based lines.** Columns are 1-based. Paths are relative to the project root.

## Tools (planned, by gorefactor's names)

Names and groups follow gorefactor. A tool is added here only when it is
implemented or being implemented.

| Group | Tools |
|---|---|
| Simplify | `rename`, `extract_variable`, `inline_variable`, `extract_function`, `inline_function`, `invert_if`, `remove_else` |
| Prepare a change | `add_parameter`, `remove_parameter`, `move_parameter`, `change_parameter_type`, `method_to_function`, `function_to_method`, `extract_struct`, `remove_dead_code`, `move_statement_up`, `move_statement_down`, `move_declaration` |
| Edit | `create_file`, `replace_statements`, `insert_statements`, `add_declaration`, `replace_expression`, `replace_text`, `add_field`, `add_case`, `add_element`, `create_from_usage` |
| Inspect | `inspect` (outline with complexity, nesting and side effects per method) |
| Multi-step | `batch` |

Java names may differ where the Java language needs it (for example, `add_field`
on a class, or `add_case` for a `switch`). Record any difference in this file.

## `inspect` (built)

Text, in gorefactor's format (decided 2026-11-02: copied, not measured).

- `file`: a header (`<file>  (package <pkg>, <n> lines)`), then every line
  numbered, except that each method, constructor and initializer body with two
  inner lines or more is replaced by `… lines a-b   [n lines]`. Bodies of
  methods in nested types are elided the same way. gorefactor also reports each
  body's complexity, nesting and side effects; not yet here.
- `file` + `start_line`/`end_line`: those lines; `end_line` defaults to, and is
  clamped to, the end of the file.
- Refused: a path that is not a `.java` file under the root; a file that does
  not exist (with the Java files in that directory); `start_line` past the end
  (with the line count); an unknown argument (with the arguments it takes).
- Not yet: gorefactor's no-argument overview, `function`, `targets`, `names`
  and `uses`.

Refusals come back as an error result whose text starts `refused: `. Other
failures start `engine error (not a refusal; ...)`.

## `batch`

- Takes a list of steps. Each step is a tool call with its arguments.
- Later steps may refer to earlier steps' new positions: `$prev.end`,
  `$helper.start`. Lines are followed through each step's edits.
- If the result does not type-check, the batch is rolled back. A retry with
  check set to `"end"` checks only at the end of the batch.
- Undo of a batch is one operation.

## Results

- Applied change: the file or files touched, and a compact diff or a count of
  changed locations. No context lines by default.
- Renames: list the locations, not full diffs.
- Refusal: `refused` with a reason and a suggested next step.
- Errors from the engine (JDT failures, missing project): a distinct error kind,
  never a refusal.

## Refusal wording

Refusals are part of the model's experience. Keep them specific ("this selection
starts inside a statement; select whole statements, lines 12 to 15") and
actionable. Avoid generic messages such as "unsupported".

## Tools that changed for the better (gorefactor evidence)

- Argument checks up front, suggested ranges, and retry with check `"end"` cut
  wasted calls.
- Stubs and `add_declaration` replacing stubs let edits start from code that does
  not compile yet.
- Coaching tips inside results did not help and were removed. Do not add them.

## Open
- Java-specific refusals (checked exceptions, overloads, generics) need their own
  wording once the first refactorings exist.
