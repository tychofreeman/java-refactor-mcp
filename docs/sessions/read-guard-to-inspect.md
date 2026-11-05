# Read guard back to refusing grep of Java

- Item: undo #201's relaxation of the read guard now that `inspect` searches.
- From: the user, 2026-11-05, after asking which greps prompted #201 and whether
  `inspect` covered them; the decision and its history are in
  `docs/how-this-project-was-built.md` ("A guard relaxed, then the search moved
  into the tool").
- Date: 2026-11-05.

## Plan

Delete `Guard.showsNoText` and its one use; the test that allowed `grep -c/-l/-q`
becomes one that refuses them. Keep the hidden-path refusal. The guard's advice
already names `pattern` and `names` (#202, #203). The same revert in gorefactor's
`cmd/read-guard`, in its #560 with its `pattern`.

## Notes

- The test through `replace_declaration`, the code through one `batch`
  (`replace_text` of the use, `replace_declaration` deleting the method).

## Measured

Not measured: one test and one batch, both through the tools.

## Assessment

Through the tools; no Java by hand. The relaxation had cost one PR in each repo
and lasted part of a day; asking what the blocked commands were for, before
offering rules, would have led to `pattern` first.

## Follow-ups

None.
