# add_parameter with type_parameter: a type argument at a call

- **Item:** issue #375 part 2, roadmap item 512
- **Date:** 2026-11-15
- **Branch:** type-argument-at-call

## Plan

`TypeParameters.toMethod` writes the declarations, parses again and refuses a call whose types
changed. Before that check, collect the calls whose types changed and that can take a type
argument, write `<value>` there (with `Type.` or `this.` where the call has no qualifier) in a
second gated round, parse again, and let the existing check judge every call. Red: two applied
goldens (a static method called qualified and unqualified; an instance method called through
`this` and another receiver), and the refusal golden moved to a case still refused (an anonymous
class calling its outer class's method).

## Notes

- read: `inspect` of `toMethod`'s check and result, `Hierarchy.isSubtype`, `Extract.typeName`.
- refusal: the inserted block declared `written`, which the method's parameter already is; the
  retry renamed it `typed`.
- slip: a stray `replace_statements` with no content, sent while reading; it changed nothing (the
  file was checked after).
- Java edits: 5 tool calls (a batch of 4 with one retry, replace_text and replace_expression for
  the result wording); none by hand.

## Measured

Not measured (no session-log hook in this session).

## Assessment

Through the tools. The second round mirrors `fullDiamonds`; the defaults that stay refused are in
the roadmap item. No probe: an explicit type argument equal to what the call had keeps its types and
target, and both are checked after the write.

## Follow-ups

If a refusal for a method reference or an outer class's method is hit in use: `Lists::<String>first`
and `Outer.this.<String>m(...)` are the next forms.
