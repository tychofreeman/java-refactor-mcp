# Extract interface and extract superclass (roadmap #85)

- **Item:** roadmap #85: a new type from members of a class, the class
  implementing or extending it. The user's list of 2026-11-04.
- **Date:** 2026-11-04
- **Branch:** extract-supertype

## Decisions (this session)

- **One tool, `extract_supertype`,** with `kind` (`interface` or `class`), over
  two tools: they take the same arguments (the class, the new type's name, the
  members) and differ in one step.
- **Composed from existing tools,** in one call (one journal entry, rolled back
  as one): the new file is written through the gate; an interface is added with
  `AddSupertype`; a superclass takes the class's place in its `extends`, and
  each member is moved with `PullUp`, so its checks apply (a member that uses
  what only the class has is refused by the gate; an override of an inherited
  concrete method by #69's rule).
- **First version limits:** a top-level class that is not generic (lifted on 2026-11-13, roadmap #144 and #145,
  `docs/sessions/extract-supertype-generic.md` and `extract-supertype-generic-class.md`); an
  interface takes public instance methods only (their signatures, with
  `throws`); the new type goes in the class's package, as a new file.

## Plan

1. Red: goldens `extract_supertype_interface`, `extract_supertype_class`,
   `extract_supertype_refused_not_public`.
2. Green: `create_file` of `ExtractSupertype`; `EXTRACT_SUPERTYPE` in `Tools`
   (`add_declaration`) and its place in `ALL` (`replace_text`).

## Notes

- `read:` the signatures of `AddSupertype.run` and `PullUp.run`, and
  `Args.list`, to compose them.
- The interface golden passed first time; the class golden showed two blank
  lines left in the class where the members were: `MemberMove.removal` took
  a member's lines but no blank line beside them. Fixed there
  (`replace_declaration`), which changed three existing goldens of `pull_up`
  and `push_down` that had the leftover blank line after `{` in their
  expected output; updated with `replace_text`.
- The goldens were red as an unknown tool, not run before the code.

## Measured

From `bin/session-report` (before the docs edits): all Java through jrefactor
(`create_file` of `ExtractSupertype`, `add_declaration` of the `Def`,
`replace_declaration` of `removal`, `replace_text` of `ALL` and of three
goldens), no refusals.

## Assessment

- **Through the tools or around them:** through; the new tool is itself a
  composition of existing ones.
- **Did the plan hold?** Yes, with the layout fix in `MemberMove`.

## Follow-ups

- Generic classes; a superclass whose constructors take arguments (the
  class's `super(...)` calls would need the new class to pass them on);
  `@Override` on the class's implementations of the new interface.
