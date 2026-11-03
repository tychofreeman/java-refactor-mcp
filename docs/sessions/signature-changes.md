# Signature changes

- **Item:** roadmap #22, position 6 of the eight the user asked for (2026-11-02).
- **Date:** 2026-11-02
- **Branch:** signature-changes

## Plan

At the highest level: changing a method's parameters means changing every
declaration in its family (what it overrides, what overrides it) and every
call, and in gorefactor that was "the most common by-hand step". Three tools,
named and argued as gorefactor's are:
- `add_parameter` (file, name, parameter, type, value, before): the parameter
  goes into every declaration of the family, before `before` or last; every call
  passes `value`, a Java expression. The body does not use it yet, so behavior
  is unchanged. Refused when a body reads a field of that name (the parameter
  would hide it).
- `remove_parameter` (file, name, parameter): out of every declaration, with its
  `@param` tag, and its argument out of every call. Refused when a body reads
  it, or an argument has side effects.
- `move_parameter` (file, name, parameter, before): reorders declarations and
  arguments. Refused when two of the arguments whose order changes have side
  effects.
`name` finds the method as `replace_declaration` does (Type.member, member,
`m(int, String)` for an overload; a constructor by its type's name).

The family: in each project file, each method declaration's binding is checked
with `overrides` against the methods of its supertypes; those pairs, by binding
key, join declarations across files, and the family is the connected set of the
target. Refused for all three: a family member that overrides a library method
(its signature is not the project's), a method reference to it (used as a
value), varargs. Calls: method invocations (also `super.m`), `new T(...)`,
`this(...)`, `super(...)`, enum constants. Every changed file gets its imports
fixed, and the whole change goes through the gate once.

Derivation:
1. Green: `Declarations.find` becomes package-private for reuse.
2. Red: register the three tools with handlers that are not written yet (let
   the stub mechanism declare them, as a check of #21), and golden cases: add
   across an interface family, add before (a constructor, with `this`, `super`
   and `new`), remove with its `@param`, move, and refusals (body reads it, side
   effects, library override, method reference, hidden field).
3. Green: `Signatures` with the family, the call finder and list edits.
4. Docs: contract, CLAUDE.md, roadmap #22 Done.

## Notes

- `refusal:` registering the tools (handlers in a class not written yet): the
  stub mechanism did not declare the class (UndefinedName) and an unexported
  JDK class was imported. Fixed first in its own PR (#19); then `Signatures`
  was created with `create_file`, since this session's server predates #19.
- `refusal:` a check-each batch in which a stub pre-empted step 2's real
  method (see the friction log); resent with check "end".
- `hand:` none. Reads: gorefactor's descriptions by `grep`; Java through
  `inspect`.
- The nine golden cases passed at the first run of `Signatures` (after the
  harness's number parsing was worked around).
- Probe: two Haiku runs ($0.004-0.005); the second, with an unambiguous
  prompt, did it in one `add_parameter` call.

