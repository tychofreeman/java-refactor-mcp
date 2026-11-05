# Session records

One file per item of work, `<branch>.md`, written by the `whats-next`
skill (`.claude/skills/whats-next`) and committed in the item's PR. A
record says what the item was, the plan made before reading code, what
happened as it happened, and what was measured, so the user can judge
whether the work went through the tools or around them.

The session-log hook (roadmap #18) records each branch's tool calls when the
hook is loaded; a session that started before `.claude/settings.json` existed
has no log, and the notes are then the record. gorefactor's records (`docs/sessions/` in
`tychofreeman/go-refactor-mcp`) show the full form, with measurements.

## Template

    # <item, in a few words>

    - **Item:** what was chosen, and from where (roadmap item N, a
      friction-log entry, a measurement)
    - **Date:** YYYY-MM-DD
    - **Branch:** <branch>

    ## Plan

    The change at the highest level that captures it, then the
    derivation: the operations, in order, that make it easy, and the one
    small edit that changes behavior. Written before reading bodies.

    ## Notes

    One line each, as it happens: `hand:`, `refusal:`, `read:`, `bulk:`,
    `plan:`.

    ## Measured

    From `bin/session-report`, or "not measured" when the branch has no session log; counts from the
    notes meanwhile.

    ## Assessment

    Through the tools or around them; large writes; whether the plan
    held; reading; one change that would have helped, and where it is
    recorded.

    ## Follow-ups
