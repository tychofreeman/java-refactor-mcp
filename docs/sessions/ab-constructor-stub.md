# A/B: a test-first constructor on a class with fields

- **Item:** #273 (a constructor stub whose arguments each fit one field assigns them) never fired
  in `docs/sessions/ab-constructors.md`: its tasks made new classes, with no fields before the
  test. Placement by kind (#279, #280) was not measured either. The user chose the run
  (2026-11-07, "what's next?", all four offered items, this one first).
- **Date:** 2026-11-07
- **Branch:** ab-constructor-stub

## Plan

A task where #273 has to fire if the model works test first: a fixture class with fields and no
constructor (`Card`: `Member holder`, `int limit`, getters), and a prompt that asks for the test
first (`new Card(member, 5)`), then a field and two methods (`borrowed`, `borrow()`), so
`add_declaration` places a field among fields. Fixture `eval-tasks/testdata/library-card` (the
library fixture and `Card`), hidden test `library-card-tests/.../CardAcceptTest`, task
`eval-tasks/library-card.json`. Validated: the hidden test fails on the fixture and passes on a
reference solution. Two runs on the default server, two on the skeleton server, Sonnet.

What to read from the transcripts: whether the test's `create_file` declared the assigning stub
and the model then wrote no constructor; whether a run sent `after` or `before`; where each field
landed.

## Notes

- The reference solution, written through `bin/jrefactor` test first, declared "Card(Member,
  int) setting holder, limit" and passed. It also showed two things for later: the stubs kept the
  implicit constructor as `Card() {}`, which nothing calls, and the field `add_declaration` added
  after `holder` and `limit` has a blank line before it, while those two have none between them.
- hand: `Card.java` went through `bin/jrefactor -C ... create_file`; the hidden test through
  `create_file` (both outside a source root of this project). Task JSON by shell.

## Measured

Sonnet, `-arms mcp -runs 2`, $0.49 in all; every run passed.

| server | run | cost | turns | #273 stub | constructor written | `after`/`before` sent |
|---|---|---|---|---|---|---|
| default | 1 | $0.108 | 11 | yes, from `create_file` of the test | no | no |
| default | 2 | $0.109 | 12 | yes | no | no |
| skeleton | 1 | $0.132 | 19 | yes, from `add_declaration` of the test | no | no |
| skeleton | 2 | $0.137 | 20 | yes | no | no |

- #273 fired in every run: "declared stubs Card(Member, int) setting holder, limit", and the first
  test passed with no constructor written (default run 2 and skeleton run 2 ran the tests right
  after and went on).
- The field `borrowed` was added with `add_declaration` of `private int borrowed;` in every run,
  and landed after `holder` and `limit` (line 8); no run named a place.
- Default run 1: `replace_declaration` of `Card.borrowed` was refused, a field and a method of
  that name; the batch `retry` with `borrowed()` passed. Default run 2: one batch, the field then
  the two bodies by line, the lines moved for it.
- Skeleton, both runs, the same four lost calls: the two stub bodies filled in parallel before
  the field existed ("borrowed cannot be resolved to a variable"), then the field and the two
  bodies again in parallel, the bodies at the lines from before the field moved them ("no
  statement starts on line 31"); then an `inspect` and the two bodies again. About seven turns
  each over the default arm, and $0.025 a run.

## Assessment

- #273 does what it was built for, every time the case arises; the model never wrote the
  assigning constructor. #279's placement went unnoticed, which is the aim: nothing to send, and
  the field where a reader expects it.
- The skeleton arm's cost here is not the skeleton rule: it is a body that uses a field nothing
  declares yet. A method or constructor a body calls gets a stub; a bare name gets a refusal. A
  field stub for an unresolved simple name in a body of the class (typed from its use, as `T.NAME`
  gets one, #107) would have made the first parallel calls pass. Logged in the friction log.
- Parallel calls at line numbers fail whenever one moves the other's lines; the batch is the
  answer the default arm found. The refusal "no statement starts on line 31" could name the line
  the statement moved to, when the file changed since the model read it.

## Follow-ups

- A field stub for a bare name a body uses and nothing declares (friction log, 2026-11-07).
- The stubs keep an implicit constructor as `Card() {}` when they add one; nothing called it here.
- `add_declaration` of a field after fields with no blank lines between them puts a blank line
  before it.
