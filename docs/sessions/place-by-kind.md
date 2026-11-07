# add_declaration places members by kind

- **Item:** the user's choice (2026-11-07), after `ab-value-classes-rerun.md` (#277): a
  replacement kept its place though the model sent `before`; the user: "I'd rather the model not
  be concerned with the layout as much as possible", then chose placement by kind with `after`
  and `before` dropped from the schema, over keeping them published and over no change. Also
  asked: the decision in `how-this-project-was-built.md`, every tool description checked against
  the behavior, and every affected document updated.
- **Date:** 2026-11-07
- **Branch:** place-by-kind

## Plan

`Declarations.plan` places each new member by kind (`rank`: field or initializer, constructor,
other; `place`: after the last member of that rank or lower, before the first member when there is
none, the end for the rest), grouping members of one place in `content`'s order. Then
`remove_parameter` of `plan`'s `after` and `before` (#272's caller passes a call, so it becomes
`null` first), the `after`/`before` locals and `Declarations.member` deleted, and the two `Arg`s
leave `ADD_DECLARATION`; `Tools.RETIRED_ARGS` and `Args.without` drop them before the argument
check, so a call that sends them is not refused. Goldens: the four `after`/`before` cases go
(one becomes `add_declaration_retired_after`), four placement cases come
(`add_declaration_field_by_kind`, `_constructor_by_kind`, `_field_first`, `_mixed_kinds`), and
`batch_lines_moved` adds a field, which now lands above the line the rename names.

## Notes

- hand: the goldens, resources, by shell heredocs and a Python script for `steps.json`.
- plan: two goldens changed by the placement, both for the better:
  `add_declaration_final_fields_replace_stub` (the fields now go after `days`, before the
  constructor) and `batch_lines_moved` (the rename's lines follow the inserted field).
- read: the server's own `tools/list` and `instructions` from the built jar, for the description
  check (the client's copies are from before the reconnect).
- plan: the description check found two out of date: `Server.INSTRUCTIONS` did not say that edits
  write constructors (#272) or that a constructor stub sets fields (#273), and `add_declaration` said
  constructors go after constructors only. `pull_up` and `push_down` say a member goes to the end
  of the target, which is still so; neither offers a place. `insert_statements`' `where` and
  `add_parameter`/`move_parameter`'s `before` choose meaning, not layout, and stay.

## Measured

`bin/session-report`: 44 tool calls; through jrefactor 3 refactoring steps and 25 edit steps (docs
included), 0 refusals; by hand 3 (the goldens); large writes 4 (the goldens' heredocs, fixture Java
in resources); denied or failed 1; reads: inspect 18, the built server's tool list 1, its
descriptions as a scratch file 1.

## Assessment

- **Through the tools:** every Java change by a refactoring (`inline`, `remove_parameter` twice,
  `replace_expression`) or an edit; the goldens by hand, as resources.
- **Large writes:** none over 20 lines of Java but the goldens.
- **Did the plan hold?** Yes, with the description fixes added.
- **One change that would have helped:** `remove_parameter` refusing an argument that is a call
  cost one preparing step (`replace_expression` to `null`); it is the documented rule.

## Follow-ups

- `pull_up` and `push_down` add a member at the end of the target (their descriptions say so);
  whether `move` of a static field and `extract_variable` with `as=field` place by kind was not
  checked. Each could use `Declarations.place`.
