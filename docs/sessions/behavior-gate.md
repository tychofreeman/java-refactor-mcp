# The behavior gate, first user (roadmap #96)

- **Item:** roadmap #96: one checker for what a refactoring does to code. The
  user's order of 2026-11-04 (the items of PR #161) and their choice of design on
  2026-11-05: declared moves (over inferring them from a diff, and over a test
  that each tool opts in).
- **Date:** 2026-11-05
- **Branch:** behavior-gate

## Plan

- `Behavior.Move(code, root, landings, label, verb)`: the code as it was, the
  code it stays inside, the nodes where it lands (none: dropped; several:
  duplicated). `Behavior.check(project, move, texts, edits)` derives:
  - copies: an effect dropped or run more than once is refused; what may throw
    and is dropped is reported;
  - order: for each landing, the effects between where the code was and where it
    lands: an effect of the code crossing one is refused; a variable it reads
    written between is refused; state it reads (it is not `fixed`) that a call
    between could change is refused; what may throw crossing an effect is reported;
  - place: an effect landing where it runs only sometimes, more than once or later
    (`Effects.sometimes`, `later`) is refused; what may throw landing there, or
    in a try that catches or finishes, is reported;
  - names: `NameCheck` of the code at the edits.
- First user: `inline_variable`, whose own read-written loop, effect refusal and
  `checkExceptional` (with its own `sometimes`) are deleted.

## Decisions (this session)

- **A probe first:** `int v = count; bump(); return v;` was inlined to
  `bump(); return count;`, which type-checks and returns one more. The old rules
  saw only direct writes to what the initializer reads. The gate's state rule
  refuses it (`inline_variable_refused_call_between`).
- **Widened:** an initializer with an effect, used once with nothing between and
  not in a branch, is now inlined (`int n = s.getAsInt(); return n + 1;`):
  the call runs at the same moment. The golden that refused it became
  `inline_variable_call_once`; `inline_variable_refused_effects_reordered` pins
  the refusal when another effect is between.
- **Wording:** the two exceptional reports of `inline_variable` changed to the
  gate's ("moves it past line 8", "where it runs only sometimes, later or under a
  catch (in a branch of an if)"); their goldens updated.
- `NameCheck` runs inside the gate, so a tool passes its edits.

## Notes

- `refusal:` the gate's first `replace_text` named `Path` without `imports`;
  resent with them.
- The goldens failed only on the two reports' wording, then passed.

## Measured

All Java through jrefactor: `create_file` of `Behavior`, a batch of seven
(`replace_text`, two safe deletes), goldens by `create_file` and `move_file`.
One refusal (imports). `InlineVariable` 375 to 267 lines.

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes.

## Follow-ups

- Move `extract_variable` (code moved before its statement: crossed are the
  statement's earlier parts), `move_statement` (each statement crosses the
  other) and `inline_function` (each argument lands at its parameter's uses) onto
  the gate, one PR each.
