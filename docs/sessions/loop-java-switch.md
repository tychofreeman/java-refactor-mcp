# The planning loop on Java

- **Item:** roadmap 520's last step: a language switch in gorefactor's planning loop
  (`eval-tasks/learning/loop.py`), then both arms on this repo's freight tasks
- **Date:** 2026-11-16
- **Branch:** loop-java-switch (this repo: the record and the roadmap); the loop's change is a
  branch of the same name in `tychofreeman/go-refactor-mcp`, worked in a separate clone, since
  another session uses the `go-refactor-mcp` checkout

## Plan

The loop knows one server and one language: it builds gorefactor's `cmd/refactor`, lists its
tools, applies a batch through it, and judges with `go vet && go test`. The change is to make
the server and the language one choice, a `-lang java` that names a server command, a tool list,
a check command, the file pattern of a place (`\.java:(\d+)`), and the source reads, with Go the
default so every existing run is unchanged.

Derivation, from the loop's names only (Python, so by hand; this repo's tools do Java):

1. Find each place the loop or `probe.py` names Go: the build, the tool list and schemas, the
   batch run, the check, `PLACE`, the `inspect` reads, the module's file listing.
2. Gather them into one value per language (a small dict or class), Go's the current values.
3. Add `-lang`, choosing Java's: `bin/jrefactor` from a path given (`-server`, this checkout),
   `bin/jrefactor list` for the tools, `bin/jrefactor -C <copy> batch @file` for a batch,
   `./mvnw -q -o test` (the task's own `check` where it gives one) for the check.
4. The one behavior change: with `-lang java`, the loop runs this repo's freight tasks. Validate
   with the free reference answers (`-models ref`, if it is that) before any model run.
5. Runs: refactoring arm and by-hand arm, sonnet, 8 attempts a task, the settings of the Go runs.

## Notes

- read: gorefactor's `loop.py` and `probe.py` (Python), to find each place that assumed Go: the
  build, the tool list, the batch's output form, the check, the place pattern, the view.
- hand: `langs.py` and the edits to `loop.py` and `probe.py` are Python, which no tool here or in
  gorefactor edits; written with a patch script that asserts each replaced text.
- plan: Java's refusals name a place as "line 41 of Depots.java" or "Truck.java:24", often with
  no path, and Maven's compile errors as an absolute path with `:[16,29]`; `Java.places` reads
  those three, a bare file name resolved to the one file of that name.
- plan: `jrefactor`'s output is not gorefactor's (`batch of N steps:` and `refused: step N of M
  (tool) refused:`); `Java.batch` judges by exit status and rewrites a refusal into gorefactor's
  `REFUSED batch at step N of M (tool):`, so `before_step` and `suggestion` read it unchanged.
- plan: the task's dir, tests and reference are found beside the task file, so the loop takes
  this repo's `eval-tasks/derivations-freight.json` in place; Go's paths are the same as before.
- plan: `-full-tools` for Java names the tools Go's set maps to here: `fold`,
  `extract_function`, `change_type`, `extract_variable` (gorefactor's `introduce_parameter`) and
  `replace_declaration` (its `remove_dead_code`), both of which are aliases here, not published.
- The free reference run (`-models ref`) passed both Java tasks, and both Go tasks run as before.

- The runs (sonnet, 8 attempts a task; gorefactor's cost-history 29, its PR #665):

  | task | tools | by hand | Go, tools | Go, by hand |
  |---|---|---|---|---|
  | tail | $0.075, 8/8 | $0.131 (median $0.067), 8/8 | $0.075 | $0.066 |
  | routes | $0.093, 8/8 | $0.372 (median $0.278), 7/8 | $0.110 | $0.552 |

  16/16 for $1.34 with the tools, 15/16 for $4.02 by hand. The tail plans went through
  `remove_parameter` with `value`, `rename` and `replace_text` in 6 of 8 attempts, `fold` and
  `inline` in 2. Seven of eight routes attempts cost one repair each: the plan replaced the field
  `road` before the accessors that still read it, under the default check "each"; the refusal
  already says `If a later step fixes these errors, send the batch with check "end"`, and each
  patch then applied.

## Measured

`bin/session-report` on this branch: 36 tool calls, 7 `bin/jrefactor` calls (inspect, list, one
deliberate refusal to see the refusal's form), no Java written. Its 7 "by hand" are the record
(Write) and Bash calls that patched gorefactor's Python in a scratch clone; its 1 "refused, then
done by hand" is that deliberate refusal followed by an unrelated Python patch. No large writes of
Java; one scratch write (`langs.py`, in the clone, which is gorefactor's PR). Two calls with no
result: a `head` of a `.java` file the read guard refused (redone with `inspect`), and this report
before it was piped.

## Assessment

- **Through the tools or around them:** the item is Python in another repo, which no tool edits;
  every Java read went through `inspect`, and the one shell read of Java was refused by the guard
  and redone. Nothing worked around the tools.
- **Large writes:** none of Java. `langs.py` (about 170 lines) is new behavior.
- **Did the plan hold?** Yes, with three additions found as it ran: `jrefactor`'s output form
  differs from gorefactor's, its refusals name places without paths, and the tasks live in this
  repo, so the loop finds them beside the task file.
- **Reading:** gorefactor's `loop.py` and `probe.py` whole, which was the job.
- **One change that would have helped:** the loop has no test of its own; a `-models ref` run of
  both languages is the check, and it took a minute and a half each time. A unit test of
  `Java.places` and `Java.batch`'s rewrite would have caught the place forms sooner. Not filed: the
  loop is gorefactor's, and its PR says so.

## Follow-ups

- Planning from names works as well on Java as on Go for these two shapes. The next question is a
  larger Java module (gorefactor's freight-xxl is five times freight-xl, where by hand cost 3.7x-11x
  the loop): a generator for a Java fixture of that size, or a real project.
- Routes plans paid one repair each for check "each"; the loop could send batches with check
  "end" by default, as gorefactor's runs may also want. A choice for the owner.
- gorefactor's PR #665 is the owner's to merge.
