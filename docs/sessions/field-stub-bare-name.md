# A field stub for a bare name

- **Item:** the friction-log entry of 2026-11-07 (A/B, library-card): under skeleton-only, both
  runs filled two stub bodies with code reading a field `borrowed` before declaring it, and lost
  four calls each to "borrowed cannot be resolved to a variable". Only `T.NAME` gets a field stub
  (#107). The user chose it from four items (2026-11-08), and which names get a stub: any bare
  name, over instance code only and over only names whose use gives a type.
- **Date:** 2026-11-08
- **Branch:** field-stub-bare-name

## Plan

The change: an edit whose code uses a bare name that nothing declares gets a field stub for it in
the named type around the use, as `T.NAME` does: `int borrowed; // not implemented`, typed from the
use, static in static code; `add_declaration` of the real field replaces it.

Derivation:
1. Red: goldens `stubs_field_bare_name` (a method returning a bare name: `int borrowed;` stub),
   `stubs_field_bare_name_static` (in a static method: `static`), `stubs_field_bare_name_increment`
   (`borrowed++;` as the only use: `int`), and `add_declaration_replaces_bare_field_stub`.
2. Green: in `Stubs.Round.find`, `UndefinedName`/`UnresolvedVariable` on a `SimpleName` used as a
   value (not a call's receiver, not a qualifier, lowercase) calls a new `bareField(unit, name)`,
   which writes the stub as `field` does, into `ancestorOrSelf(name, AbstractTypeDeclaration)`.
3. Green: `resultType` gives `int` for the operand of `++`/`--`, and the other side's type for an
   assignment's left side, if it does not already.
4. Refactor: `field` and `bareField` share the stub's writing.
5. Docs: `Server.INSTRUCTIONS` if it lists what gets stubs, the contract, the session notes'
   stubs decision, the friction log's follow-up.

## Notes

- read: `Stubs.Round.find`, `field`, `removed` and `resultType` (180-370, 639-723): where a
  `T.NAME` stub is made and how a use is typed.
- bulk: `bareField` (21 lines, `add_declaration`), new behavior, mostly `field`'s shape again.
- plan: `removed` now also finds a field, so a field an edit deletes is not declared again as a
  stub; `resultType` already typed a value, and gained an assignment's left side and `++`/`--`.
- plan: step 4, sharing the stub's writing between `field` and `bareField`, was not done: they
  differ in owner, `static` and `visibility`, and share three lines.
- The stub after a field lands after a blank line, as any field added among fields does (an open
  follow-up of placement by kind); the golden pins it.
- hand: none in Java; one `-Werror` warning (a raw `Stream`) fixed with `replace_statements`.
- No existing golden changed: no refused case relied on a bare unresolved name.

## Measured

From `bin/session-report`: 12 edit steps through jrefactor (one batch of five for the code, one
fix, one batch of six for the docs), no refusals; by hand only the golden fixtures (resources, by
shell; 23 lines of Java test input) and this record; reads: 9 `inspect`; one `grep` of a test
file refused by the read guard, answered with `inspect` `pattern`.

## Assessment

- **Through the tools or around them:** through them; one batch of five for the code, one fix.
- **Large writes:** `bareField`, new behavior.
- **Did the plan hold?** Yes, but for the shared helper, left out as not worth it.
- **Reading:** only what the new behavior needed.
- **One change that would have helped:** none.

## Follow-ups

- An A/B rerun of `eval-tasks/library-card.json` on the skeleton arm would show whether the four
  lost calls are gone (costs money; ask first).
- A bare name used as a call's receiver (`borrowed.add(b)`) gets no stub: its type is unknown.
