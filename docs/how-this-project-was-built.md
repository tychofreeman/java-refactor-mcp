# How this project was built

A record of the practices behind java-refactor-mcp, written to decide which
are worth repeating, as gorefactor's `docs/how-this-project-was-built.md`
does for the Go project. It draws on the git history, `docs/friction-log.md`,
`docs/session-notes.md`, `docs/design.md` and the records in `docs/sessions/`.
It was started on 2026-11-04, covering the work from 2026-11-02 (PRs #1-#103), and
has been extended since; it now runs through PR #259 (2026-11-06).
Judgments are Claude's; figures are from the records named beside them.
Extend it in the PR that changes a decision, as gorefactor's was: add a line to
the arc, and a dated section in order when the decision needs its reasons.

`#N` is a pull request of this repository; `roadmap #N` is a row of the roadmap in
`docs/design.md` (its `#` column); a gorefactor PR is named as such.

## The arc in brief

1. **2026-11-02: the safe core.** JDT Core headless, a type-check gate
   (`TypeCheck`, `Gate`) that refuses any change which adds a compile error,
   `rename`, and the MCP server over STDIO. The spike
   (`spikes/jdt-headless/`) showed bindings resolve with no workspace.
2. **2026-11-02: edits and their support.** `create_file`, `replace_text`,
   `add_declaration`, `replace_declaration`, with imports fixed by every edit
   (`Imports.fix`) and stubs declared for what an edit uses (`Stubs`).
   `batch` and `undo` came with a journal, so every write is undoable.
3. **2026-11-02 to 11-03: signatures, moves and extraction.** `add_parameter`,
   `remove_parameter`, `move_parameter` across overrides and implementations;
   `extract_method`; `move_type`; `pull_up` and `push_down`, for methods,
   fields and overloads (PRs #99-#102).
4. **2026-11-03: performance and measurement.** A narrowed type check (#61,
   fixed in #67 after a stress run found its first rule missed three of 60
   random edits through inheritance); a classpath cache; `bin/jrefactor` for sessions
   where the server cannot run; a session log of every tool call. Measured
   costs are in `docs/sessions/measure-call-cost.md` and
   `docs/sessions/cli-cache.md`.
5. **2026-11-04: the process was checked.** The roadmap's open items were
   closed or split, and the tool-count limit was set at 50 with `ToolsTest`
   (PR #103). A comparison with gorefactor (Go and TypeScript), Fowler's
   catalog and IntelliJ found about 35 refactorings that are composites of
   our tools, most of them through `replace_text`.
6. **2026-11-04: the ten higher-level tools** (PRs #105-#115), one PR each,
   in the user's order: `extract_variable`, `inline_variable`,
   `inline_function`, `change_type`, `add_supertype`, `move_statement`,
   `invert_if`, `remove_else`, `move_member`, `encapsulate_field`. Each was
   written by hand: no tool writes a new tool.
7. **2026-11-04: behavior that type-checks.** Probes after the stress run
   found seven rewrites that compiled and changed behavior (effect order,
   name capture). They became refusals (#116, #119, and later read-order and
   pull-up fixes). Then the user set the policy on what a refactoring must
   preserve (below), and a review of the code's abstractions put two shared
   shapes ahead of the flag that policy needed (roadmap #71 and #72, then #73;
   PRs #127-#129).
8. **2026-11-04: the missing refactorings.** The notes were pruned (#131,
   #132). Then roadmap #80-#91, in the user's order (#136-#151): a type to
   another package, `move_file`, a signature type change, safe delete,
   `extract_supertype`, introduce constant, field and parameter,
   `convert_lambda`, `convert_to_record`, `extract_struct` and extract class,
   static and instance in place, and the edit primitives. `NameCheck`, one
   check that moved code keeps its meaning (roadmap #77, #158-#160).
9. **2026-11-05: the A/B runs and what they found.** Three runs of
   gorefactor's harness on a Java port of its warehouse traps (#166, #179,
   #183); between them, the mechanisms the runs named (roadmap #99-#103) and
   the behavior gate, where a tool declares the code it moves (roadmap #96,
   #173-#176). The tools' extra cost fell from 31% to 2% (below). Every push
   to `main` that changes code is now released (#182).
10. **2026-11-05: probes as tests.** Behavior probes became `ProbeTest`, run
    with every build (#192), and were written for every tool that moves code
    (#193, #195). They found bugs that type-checked: `fold`'s overload
    resolution (#186), `move_statement` past an array alias (#193), `rename`
    into an overload (#195) and capturing a name (#197, #200), and
    `change_type` of a primitive (#196).
11. **2026-11-05: the guards, and search moved into `inspect`.** The read and
    write guards (#185, roadmap #15 and #104); a relaxation of the read guard
    (#201), reverted once `inspect` gained `pattern` and `names` (#202-#204;
    below).
12. **2026-11-05: the friction log triaged.** Every entry got a follow-up (#206),
    and the open gaps became roadmap #105-#111, ordered by how many entries name
    them; the first, ambiguous imports, was built next on the user's rule (roadmap
    #105: choose on evidence, else refuse with every name).
13. **2026-11-05: from refactorings to transformations.** The user widened the
    thesis: a model needs operations that construct code as well as ones that
    change it, typing only expressions and statements, as in an IDE. A Java file
    then starts as a declaration, and tests from their target (design note
    "Constructive transformations"; roadmap #114-#116; the conversation that led
    there is "From refactorings to transformations" below).
14. **2026-11-06: parity with Go and TypeScript.** The user had gorefactor's
    cross-language list (its `docs/parity.md`) merged into this roadmap as rows
    #119-#130 (#222), then had them built in order (#223-#242): one name per
    operation, the more generic one, with the old names kept as unpublished aliases
    (`extract_function`, one `move`, one `inline`); the member-level operations the
    transformations thesis asked for (`implement` and `constructor` on
    `add_declaration`, `surround` on `replace_statements`); `move_member` to another
    file and for static fields; `add_result`; `type_switch_to_method`; inline class
    as `inline`'s field route; `add_parameter` on records. Item #130 was left until
    friction raises it.
15. **2026-11-06: the evidence a tool chooses on, and a second try.** Ambiguous imports
    learned to go by the package's other files (#245) and to prefer the project's own
    class (#244); a refused batch can be resent with only its changed steps (`retry`,
    #247, #251).
16. **2026-11-06: `replace_text` takes Go's spacing rule.** Roadmap #106 first got a
    uniform indent shift (#221, #223); the user then replaced it with gorefactor's rule
    (#254; below).
17. **2026-11-06: what the runs said about the new operations.** The merged tool list
    cost nothing measurable (#243); on tasks built for the constructive operations
    (#252) no model used them, and only a refusal steered to one (#250, #253). The
    skeleton refusal was widened to typed bodies a command writes (#255; below).
18. **2026-11-06: descriptions say what a tool does.** The user asked why the
    descriptions were so long, and changed the rule: refusals explain themselves,
    and what every tool shares is said once, in the server's instructions (#256-#259;
    below).

## Why `replace_text` carried so much (2026-11-04)

This is the record the user asked for on 2026-11-04. Three things explain it.

- **It was the only edit that took text.** `replace_text` was built in
  roadmap #19, with `create_file`, `add_declaration` and
  `replace_declaration`. There was no edit addressed by a node in the
  syntax tree. Anything inside a body that was not a whole declaration could
  only be changed by replacing its text, so every composite that touched an
  expression, a statement or a call site reached for it.
- **The rule written down allowed it.** CLAUDE.md says `replace_text` is for
  "a few lines, literals, comments and non-Java files". Literals and short
  edits inside bodies fell under "a few lines", so the wide uses were not
  against the rule as written. The rule did not say that a composite would
  be built from it.
- **It passed the gate, so nothing failed.** A `replace_text` that left the
  code compiling was accepted. The cost was not visible in a result: the
  model wrote the old text and the new, and the gate could not tell which
  occurrence was meant. The cost shows only in tokens and in review.

We did not measure how often it was used for a composite. The friction log
was not searched for it before the 2026-11-04 comparison, so the claim
"most of them through `replace_text`" is from reading the composites, not
from counts. A count is the first thing to add to the records.

## The decision: ten higher-level tools (2026-11-04)

On 2026-11-04 the options were:

- **A. Three primitives:** `replace_expression`, `insert_statements`,
  `remove_statement`. Cheapest in tools. Each composite stays a batch of
  text-free but still hand-written steps.
- **B. One tool per refactoring:** `extract_variable`, `inline_variable`,
  `inline_function`, `change_type`, `add_supertype`, `move_statement`,
  `invert_if`, `remove_else`, `move_member`, `encapsulate_field`. The
  most tokens saved, and the most tools to test. Chosen by the user, who
  wants the model to work "at a higher level of abstraction".
- **C. Primitives first, then refactorings on friction-log evidence.** The
  lowest tool count, and the one CLAUDE.md's evidence rule favours. Not
  chosen.

The user chose **B** over **A** and **C**, and raised the tool-count limit
to 50 to allow it. Under B, each tool is its own PR, in the order the user
gives, with golden cases first. The names are the working list from the
comparison, not final: each tool's design note decides its name and
arguments.

The choice is in tension with CLAUDE.md's "do not add tools speculatively".
Ten tools are not speculative if the composites in the comparison are
real uses, but the friction log has not yet been counted to show that.
That count is the first step before the first of the ten is merged, and
the result goes in the first tool's record.

## The stress run sees compile errors, not behavior (2026-11-04)

`RefactoringStressTest` ran 220 calls of the new tools on Commons Lang and
Math with no failure, and probes written by hand then found seven changes in
behavior that type-checked (`docs/sessions/stress-new-tools.md`): an
argument's call dropped, two calls reordered, `half(3)` turned into integer
division, a name binding to another class's field, a read moved ahead of an
increment, a local hiding a field. The gate cannot see these, by design. The
lesson, recorded in the session notes: a tool that moves expressions or
members needs probes for evaluation order and name capture, not only a
stress run. The fixes were refusals, not rewrites, and are conservative: a
safe case of the same shape is refused too.

Two sessions fixed the same roadmap row (roadmap #67) at once that day. The user
kept one session's refusals and closed the other's PR (#118). Since then,
a session checks `main` and the open branches for a row before fixing it.

## What a refactoring preserves (2026-11-04)

On 2026-11-04 the user set the rule, as an extension of "behavior-preserving
transformation": no transformation keeps every kind of behavior, so a
refactoring is defined by the kinds it keeps. Normal logic flow (calls,
assignments, reads and their order) is kept. Exceptional behavior is kept
only where a guard or a try/catch makes it part of the normal logic;
elsewhere it is a concession, made knowingly and reported. The model can
require it with `preserve_exceptions`, and a tool that changes it without
the flag names each change in a separate part of its result. The options
the user chose between (one flag or one per kind; the alert in the text or
apart) are in `docs/design.md`, question 0.

## Abstractions before the cross-cutting flag (2026-11-04)

Building the flag on the code as it was would have meant editing fourteen
`run` methods and every tool schema, and adding a fifth partial model of
effects. A review of the abstractions (2026-11-04) found the infrastructure
well factored (`Jdt`, `Gate`, `Journal`, `TextEdit`, `Lines`) and the tool
layer without a shared shape: each tool parsed and located its target its
own way and refused from inside its algorithm. The user agreed to build two
abstractions first, each behavior-neutral and checked by the goldens:
`Opened`/`Precondition` (roadmap #71, #127) and one `Effects` analysis (roadmap
#72, #128). The flag (roadmap #73, #129) then attached once, in the `Def` constructor. All three were done by
another session the same evening, through the tools.

## Through the tools, or around them (2026-11-04)

The ten tools and the effect-order refusals were written by hand, by Edit
and Write, in a session that had no java-refactor-mcp tools loaded. One of
its records first called that work "through the tools"; it was corrected
(#123). The sessions that built roadmap #71-#73 had the tools and made almost every
Java change through them (21, 19 and 53 edit steps, plus `add_parameter`
and `inline_function`, by `bin/session-report`). That is the first
evidence in this project of the difference the user asked about: whether a
session thinks about changes as operations when it has the tools, or as
text when it does not. It is not yet a measurement of token cost; the A/B
harness (roadmap #11) is.

## What every session loads (2026-11-04)

By the evening of 2026-11-04 every session loaded `CLAUDE.md` (222 lines) and
the session notes it imports (561 lines, about 5,900 words) before doing
anything. Most of the notes were a history of each item ("X exists, roadmap
#N, date, measured Y"), written there because the notes were the first place
sessions recorded what they learned, before the records and this document
existed. The user agreed to prune both (#131 and the PR after it): the notes
now hold only preferences, the user's decisions, rules for where code goes,
testing practice and traps (157 lines), and say that history goes in the
records and here. `CLAUDE.md` lost its tool lists, which named 13 of 25 tools,
and the text it said twice.

## The A/B runs (2026-11-05)

The user asked for it. gorefactor's harness ran this server with no change
but a two-line Bash allowlist and a wrapper for its `-C` flag, on a Java port
of gorefactor's warehouse traps. Sonnet passed every trap in both arms; the
tools cost about a third more per run ($0.12 against $0.09). The traps were
where the tools paid their way in correctness and lost in cost: they
refused each unsafe step (the reordered `p.bin()` calls, the short-circuited
`charge`, the captured `markup`), and the model then did by hand the
extraction the refusal asked for, because `extract_variable` refused that
too. As in gorefactor, the run's value was the list of mechanisms it found
(roadmap #99-#101), not the verdict. Details in `docs/sessions/ab-warehouse.md`.

Two more runs measured those mechanisms (#179, #183). With roadmap #99-#101 the
tools' extra cost was 24%; with `inline_function` binding an argument to a local
(roadmap #102) and `fold` (roadmap #103), 2%: $0.095 per run against $0.093.
`inline` became cheaper than by hand ($0.077 against $0.086); `dedupe` stayed
dearer ($0.112 against $0.085), and one of its runs merged less than the task
asked. The set is small and every trap was handled safely in both arms, so the
result is parity on it, not a saving. Details in `docs/sessions/ab-warehouse-2.md`
and `ab-warehouse-3.md`.

## A guard relaxed, then the search moved into the tool (2026-11-05)

The read guard (roadmap #15) denies reading a `.java` file other than
through `inspect`, `grep` included. On 2026-11-05 a session hit it five
times and logged the friction: a `grep -c DEBUG` of `Rename.java` (to
confirm a temporary debug line was gone, a count with no source text), a
`cat` of golden Java through a shell variable, and three `grep -n` lookups
for a line number (where `Stubs` decides `static`, a description in
`Tools`, the overload check in `Fold`). The session offered three rules:
allow only the greps that print no text and explain the hidden path
(recommended), allow `grep -n` of Java too, or add a search to `inspect`
and point the refusal at it. The user accepted the recommendation, and the
session made it in both guards: #201 here, gorefactor's #559.

The user then asked which greps had prompted the change, and whether
`inspect` did not cover that use. It did not: `inspect` took a file and
gave an outline or a range, so a search took two calls, and in practice
the session had piped `bin/jrefactor inspect` through `grep`, which the
guard allowed and which saved nothing. The user decided to undo the
relaxation and move what it allowed into the tool: `inspect` gained
`pattern` (#202: the lines that match, in a file or the project, each
file's count first, which covers `grep -n`, `-c` and `-l`) and `names`
(#203: a declaration by name, wherever it is), gorefactor's `inspect`
gained `pattern` (its #560), and both guards went back to refusing every
grep of source, with advice naming `pattern`. Only the clearer refusal
of a hidden path was kept.

The lesson matches gorefactor's: when a guard gets in the way, the first
question is what the blocked command was for, and whether the tool should
answer it. Relaxing the guard made the friction go away without making the
work go through the tools; the tool option, which the session had ranked
last as "more work", cost two small PRs.

## From refactorings to transformations (2026-11-05)

The question started with tests. The user observed that the model wrote each test
wholesale, and asked what the options were. The session offered four: copying a
declaration with substitutions (most new tests are a neighbour with a few values changed),
tests as table rows (`add_element`), snapshot tests recorded from a call, and a test
skeleton from a signature. It recommended the first. Asked how a new Java file is made
today, the session answered `create_file`, and found on the way that a class stub declared
from a test lands in the test tree (roadmap #113, #211).

The user then named `create_file` as a loophole: the one place the model writes a lot of
code. The session framed it as retyping and measured it. About 400 `create_file` calls had
written about 11,000 lines of Java since the session log began, the largest a 592-line tool
in one call. The options it offered were a skeleton-only `create_file`, a refusal for code
that repeats existing lines (with a copy tool for the next step), a size cap, and measuring
first. It recommended measuring, then the refusal.

The user corrected the frame. The worry was not retyping. It was that the model has no
tools to describe what it wants at a high enough level of abstraction, so the thesis may be
that transformations as a whole, not only refactorings, enable higher-level thinking. The
user's picture was a good IDE: "I only type things sometimes. But the tricky bit is that I
do type code sometimes. When I do, I'm thinking about what the structures are, not what
letters I'm typing." Seen that way, the size cap became the right direction, sharpened: a
file starts as a type's declaration, members come from operations (implement, stubs from
usage, a test from its target), and typing is left to statements and expressions. The
session's table of IDE moves by level (file and type, members from structure, members from
usage, tests, statements, expressions) showed `create_file` as the one level with no
operation at all.

The user approved the description and the first slice: the design note "Constructive
transformations" in `docs/design.md` (#212), `create_file` with `declaration` (#213),
`test_for` on `create_file` and `add_declaration` (#214), and skeleton-only creation behind
`JREFACTOR_SKELETON_ONLY=1` (#215). The measure changes with the thesis: beside the share
of edits made through the tools, the share of typed text that is statements and expressions
rather than structure. Testing it needs feature tasks, not refactoring traps, so the next
step was porting gorefactor's shop feature tasks to Java and running an arm with the flag on.

The first measurement of the slice (shop feature tasks, Sonnet, 2 runs each) found every arm
passing and the tools dearer: manual $0.107 per run, mcp $0.170, skeleton-only $0.217. The
skeleton rule worked as a refusal but, with only the file-level operation built, moved typing
from `create_file` to `add_declaration`; `test_for` went unused. One run made its new type by
transformation (`extract_struct`, `move_type`) until a gap on records stopped it. The thesis is
neither supported nor refuted by this: it waits on the member-level operations
(`docs/sessions/ab-shop-tasks.md`).

The lesson for this project: when the user names a symptom ("a loophole for writing code"),
ask what it is a symptom of before measuring it. The session measured the symptom it
assumed, retyping; the user's concern was the vocabulary.

## One list for three languages (2026-11-06)

gorefactor's `docs/parity.md` compares the Go, TypeScript and Java servers. The user asked for
it to be found and folded into this repo's next items; it became roadmap #119-#130, ordered by
evidence with the friction log's rows (#222). Each row that could go more than one way was put
to the user as options, and the choices are recorded with the options they beat: one name per
operation, the more generic (so `extract_function`, with `extract_method` kept as an alias that
`batch` and the CLI accept but the tool list does not publish); one `move` and one `inline` that
route by their arguments, as gorefactor's `Router` does; `implement` and `constructor` as options
on `add_declaration` rather than new tools; a record for `add_result`; `type_switch_to_method` as
one tool; inline class as a route of `inline`, with `split` deferred; `add_parameter` on records,
not a new tool; and for extract class on a record, a flag (`value_semantics`) to accept the
changed `toString` (#220). Aliases let a name change without breaking the goldens, the A/B
harness's instructions or another session's habits, and refusals name only the published names,
since a model calls only what it is shown.

The merged list was A/B-checked before more was built on it (#243): 24 runs, every one passing,
warehouse at $0.098 a run against a latest baseline of $0.095, shop 6% dearer, within the spread
(`docs/sessions/ab-merged-tools.md`). Its one failure named an argument `move` did not take
(`imports`) and suggested a test library's class over the project's own; both became #131 and
were fixed the same day (#244).

## `replace_text`: from a uniform shift to Go's rule (2026-11-06)

The most frequent resend in this project's own work was an `old` copied from `inspect` with its
indentation off (`inspect` puts two spaces between the number and the code). Roadmap #106 offered
three rules: a uniform indent shift (every line off by the same amount), any spacing, or a better
refusal. The user chose the uniform shift, which #221 and #223 built (`IndentShift`). Later in the
day the user replaced it with gorefactor's rule, any run of spaces or tabs standing for any other
within a line, so the three servers agree; asked what to do with `new` after a loose match, the
user chose re-indenting it to the file over writing it as sent or refusing (#254). The shift
lasted a few hours; the cost of the change was small because the matching was in one class. The
lesson: a parity question is worth asking before a rule is built, not after.

## The constructive operations, measured (2026-11-06)

The transformations thesis (above) waited on member-level operations; by midday they existed.
Two measurements followed. The skeleton arm on the shop tasks was rerun (#250): every run passed,
cost fell from $0.217 to $0.186 and typed lines by 29%, but none of `implement`, `constructor`,
`surround` or `test_for` was called; the gain came from the day's other work. So a task set was
written to call for them (#252, `eval-tasks/library.json`: an interface to implement, a class from
its fields, a try around a call, a test). On it (#253) manual passed 8 of 8 at $0.082 a run, the
tools' arm 6 of 8 at $0.097, skeleton-only 7 of 8 at $0.117, and again no model used the options;
the lost runs were heredocs the write guard refused.

The finding is gorefactor's, met again: a description does not change what a model does; a
refusal that names the alternative does. Skeleton-only was the one mechanism that had moved
typing, so the user had it widened (#255) to the typed bodies a command writes: a constructor
that only assigns fields (naming `constructor`), an override that only calls `super` or throws
(naming `implement`), and statements wrapped unchanged in a try or a loop (naming `surround`).
Asked whether to refuse any body typed into a `.java` file, the user said no: only the bodies a
command would write, since the thesis is that typing is left for statements and expressions, not
that typing is wrong. The next run (`docs/sessions/ab-library-skeleton.md`) measured it: every
refusal that named an operation was followed by it (`constructor` twice, `surround` once,
`declaration` four times), all 8 runs passed, and each steer cost a turn ($0.126 a run against
$0.117). The member operations carried work for the first time, through refusals alone.

The compile refusals were then made to name the operation too: blank `final` fields name
`constructor` (#265), and `@Override` methods in a type with no such supertype name `add_supertype`
(#267, #268). A rerun (#269, `docs/sessions/ab-hints.md`) found each hint followed in the next
call, and one way around the rule: a model refused a typed constructor sent the whole class
through `replace_declaration`, which skeleton-only did not check. The checks now run there too
(2026-11-07). A rule that steers is only as good as its coverage of the edits that can carry the
same text.

The user then asked why a model should have to ask for a constructor at all. A refusal that
names the operation still costs a turn, and where the tool can tell what is meant it can just do
it: an edit that leaves new final fields unset in a class with no constructor now writes the
constructor, and says when the class would fit a record (2026-11-07). Steering by refusal is for
choices the model has to make; when only one result makes sense, the tool should produce it.
The user took the same step for stubs: a constructor declared from a test's call, whose
arguments each fit one field, assigns them. It is the first stub that is real code, a guess
kept to the cases with one answer.

## Descriptions say what a tool does (2026-11-06)

The user asked why the descriptions of `create_file` and its neighbours were so long. The answer
was that each carried its refusals (CLAUDE.md had asked for that) and repeated what every tool
shares: that it type-checks, declares stubs, fixes imports, and what a refusal is. All of it
together was about 58,000 characters, sent to every session. The user changed the rule in
CLAUDE.md (#256): a description says what the tool does, its arguments' meaning and an example;
a refusal explains itself when it happens, and `docs/tool-contract.md` keeps the lists. What
every tool shares went into `Server.INSTRUCTIONS`, about 530 characters sent once as the MCP
server's instructions. The leftover refusal wording and boilerplate went in #257, and every
`preserve_exceptions` description was cut to its first clause (#258); the total is now about
42,400 characters.

The user then asked whether `extract_struct`'s three cases (a parameter object, extract class,
a record's components) meant anything different to a model. They did not: each grouped some
members of a thing into a new type. The tool took one shape (#259): `name`, `members`, `struct`
and `as`, the case following from whether `name` is a class, a record or a method; the old
argument names are renamed on the way in (`Tools.ARG_ALIASES`), as tool aliases were. This
continues the parity choice of one name per operation down to the arguments. Asked next whether
to rename the tool, the user chose `extract_type` (#262): "struct" is Go's word, the new type
is a class or a record, and parity is kept where it makes sense, not for its own sake; the new
name is meant to replace `extract_struct` in gorefactor too.

The lesson: length in a description is not guidance a model uses (the A/B runs had shown that),
and it is paid on every turn. When a description grows, ask whether the text belongs in the
refusal, the shared instructions, or the contract.

## Open at the time of writing (2026-11-06)

- `CLAUDE.md` still lists literals as a `replace_text` use, though
  `replace_expression` exists since the edit primitives (#150).
- The shorter descriptions (#256-#259) have not been A/B-checked against the baselines.
- The widened skeleton refusals (#255) steer to `constructor` and `surround`, a turn each; whether a
  model refused once starts with the operation next time is not measured. Only `test_for` has
  been used unprompted.
- `extract_struct` (1,913 characters) is still above gorefactor's per-tool cap of 1,700, and no
  test here enforces a cap.
- `docs/friction-log.md` was never counted for `replace_text` use before the
  ten tools were built; the claim in "Why `replace_text` carried so much"
  rests on reading the composites.
- The A/B runs used one small task set, at parity in cost after the third
  run; whether the tools save cost on larger feature work is not measured
  (gorefactor's `ab-features` set is not ported).
