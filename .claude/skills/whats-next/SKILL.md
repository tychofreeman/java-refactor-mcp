---
name: whats-next
description: When the user asks "what's next?" (or /whats-next), offer the next items of work with their evidence, let the user choose, then do the chosen item while keeping a session record in docs/sessions/ that shows whether the tools were used or worked around.
---

# What's next

Two halves: choosing the item with the user, then doing it with a record
that lets the user judge afterwards whether the work went through the
tools as this project intends, or around them. Adapted from gorefactor's
skill of the same name (`tychofreeman/go-refactor-mcp`,
`.claude/skills/whats-next`); keep the two close.

## 1. Find the candidates

Quietly, without reading Java:

- **Open PRs** (`gh pr list`, or `git branch -r` when the API is closed):
  work waiting for the user to merge is not a candidate, and a later item
  that builds on one waits too.
- **The roadmap** (the table under "Roadmap" in `docs/design.md`): rows
  whose status is not *Done*, with *Next* first, then *Planned* items
  whose prerequisites are done.
- **The open questions** in `docs/design.md` that a roadmap item waits on.
- **"Next"** in `docs/session-notes.md` ("Where the work stands").
- **Follow-ups** of the latest records in `docs/sessions/`.
- **The last few entries of `docs/friction-log.md`**: a gap named there
  and not yet on the roadmap.

Skip anything CLAUDE.md lists as not to do.

## 2. Offer them

Ask with AskUserQuestion: two to four options, the one the evidence
favours first, marked "(Recommended)". Each option's label is a few words;
its description is one line with the evidence (the roadmap item, friction
entry or measurement it comes from) and what it costs: an A/B run costs
money, and per CLAUDE.md needs the user's yes anyway. If the user picks
"Other", take their words as the item.

## 3. Start the record

Before reading any code for the item:

1. Branch from an up-to-date `main`, one branch per item. If the item
   builds on a PR not yet merged, branch from that PR's branch, and say so
   in the PR.
2. Create `docs/sessions/<branch>.md` from the template in
   `docs/sessions/README.md`: the item, where it came from, the date, and
   **the plan**. Name the change at the highest level that captures it
   ("this tool needs a project loaded once per server, not per call", not
   "add a field to `Server`"), then the derivation you intend: the
   operations, in order, that make the change easy, and the one small edit
   that changes behavior. Write the plan from names and signatures (an
   outline of the classes involved, not their bodies); it is what the work
   is judged against, so write it before the bodies.

There is no session-log hook here yet (roadmap, "Session log"), so
nothing records the calls on its own. Until there is, the notes below are
the only record; keep them complete.

## 4. Work, and note what a log cannot see

Work as CLAUDE.md says (TDD's order; every refactoring and edit through
java-refactor-mcp once the server runs). Until the server runs and covers
an edit, Java is necessarily written by hand: note it anyway, with the
tool that would have done it, since that is the evidence for which tool
to build. Append one line to the record's "Notes" whenever one of these
happens, at the time, not reconstructed at the end:

- `hand:` Java written or changed outside java-refactor-mcp: what it was,
  and, when a tool exists, the compositions of two or three tools you
  tried first and why each failed. Before the server runs, name the tool
  (here or in gorefactor) that would have done it.
- `refusal:` a java-refactor-mcp refusal, and what you did next: another
  operation, a preparing step, undo and reorder, or by hand. Say which.
- `read:` a body you read, and the question it answered. Reading to
  write the new behavior is expected; reading to find uses or check that
  a refactoring is safe is what the tools should do for you.
- `bulk:` a write of 20 lines of Java or more in one call, by any route
  (Write, Edit, a heredoc, a tool's create or replace): whether the lines
  are new behavior or existing code typed out again, and, if existing
  code, which operation could have moved it and why it was written
  instead. "Writing it out was easier than the edits or refactorings" is
  a valid answer; say it when it is the reason.
- `plan:` the plan changed, and why.

A line is a sentence. Write it as it is, including when the tools were
skipped because writing it out felt quicker: that is the evidence the
record exists for.

## 5. Finish the record

Before pushing:

1. "Measured": with no session-log hook yet, write "not measured" and
   count what you can from the notes: hand writes of Java, refusals,
   `bulk:` writes. When the hook and a token counter exist, their report
   goes here instead.
2. Write "Assessment", a few lines, plainly, including what went badly:
   - **Through the tools or around them:** writes by hand, and for each,
     the tool or composition that fit, or the gap; refusals followed by
     hand edits.
   - **Large writes:** take each `bulk:` note and answer: new behavior, or
     existing code written again? If existing code, was it written
     because writing lines was easier? Name the operation that would
     have moved it, or the gap if none would.
   - **Did the plan hold?** Which operations it named were used, and what
     replaced the others.
   - **Reading:** bodies read for what the tools would have answered.
   - **One change that would have helped:** a tool, an option, a refusal
     that names the next step, or an instruction. Put it on the roadmap
     or in the friction log, as CLAUDE.md says, and link it here.
3. "Follow-ups": what is left of the item, for the next "what's next?".
4. Commit the record with the work, in the same PR. The friction-log
   entry is still written as CLAUDE.md asks; it may point to the record.
5. Update `docs/session-notes.md` ("Where the work stands") and the
   roadmap status in the same PR.

## What "working around the tools" looks like

Use these when writing notes and the assessment; they are what the user
asked the record to show.

- **Embracing:** the change was planned as operations before bodies were
  read; refactorings carried it to every use; the only text written was
  the new behavior; a refusal was answered with another operation or a
  preparing step, or with undo and a different order.
- **Working around:** Edit, Write or a shell command changing Java that a
  tool covers; a refusal followed by a hand edit that makes the same
  change; rewriting existing code an operation could have moved (the same
  edit at many sites is the clearest sign); writing a whole file or body
  again to change a few lines of it; reading whole files or many bodies
  before planning.
