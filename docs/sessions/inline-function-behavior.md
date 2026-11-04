# inline_function keeps what the call did (roadmap #67)

- **Item:** roadmap #67, found by the probes of
  `docs/sessions/stress-new-tools.md`: `inline_function` applied four
  inlines that type-check and change behavior. The user asked this session
  to fix #67-#70 (2026-11-04), though the tool is the other session's.
- **Date:** 2026-11-04
- **Branch:** inline-function-behavior

## Plan

The change: an inlined call evaluates what the call evaluated, in the same
order, with the same names meaning the same declarations. Where the text
cannot keep that, refuse and say to name the argument first (an
`extract_variable` step).

Rules, by argument (the body is one return or one expression statement):

- An argument is *fixed* when it is literals, locals and parameters only
  (no call, field, array element, assignment). A fixed argument goes in as
  now.
- An argument that is not fixed (it has an effect, or reads state an
  effect could change) is refused when: it has an effect and its parameter
  is used other than once (0: dropped; 2+: as now); its use is inside a
  lambda, an anonymous class, a `?:` branch or the right of `&&`/`||`; an
  effect of the body comes before its use; or the uses of such arguments
  are not in argument order.
- A parameter the body assigns is refused.
- A primitive and a different type (`3` for `double x`): the argument is
  cast, `((double) 3)`. A reference argument whose type is not the
  parameter's, where the parameter is passed to a call (overloads may pick
  another), is refused.
- A static field or method of the declaring type (or its supertypes) named
  unqualified in the body is qualified (`Lib.FACTOR`) when the call is in
  another type or its method declares that name. One reached by static
  import is refused.
- A variable the body declares (a lambda's parameter) with the name of
  one an argument reads is refused.

Derivation:

1. Red: golden cases `inline_function_refused_unused_effect`,
   `_refused_reorder`, `_refused_effect_before`, `inline_function_cast`,
   `inline_function_qualifies`, written with `create_file`.
2. Green: `replacementFor` takes the rewrites as one list (parameter uses
   and qualifications), so the substitution loop stays one loop; a new
   `checkArguments` holds the refusals; the cast goes where `argText` is made.

## Notes

- `read:` `InlineFunction` lines 36-94 and 260-312 (`run`'s checks,
  `checkNesting`, `paramUsesIn`) and `ExtractVariable.hasEffect`: to write the
  new rules beside the old ones.
- `bulk:` 24 `create_file` steps for five golden cases (in one batch): new
  tests.
- `bulk:` the rules, about 330 lines in one `add_declaration` (14 helpers),
  plus four `add_parameter` steps and two `replace_text` in the same batch:
  new behavior, except the substitution loop, which `replace_text`
  rewrote to take qualifications too.
- `plan:` two goldens failed only on a blank line left after the method,
  when the method is the class's last member (`removal`, existing). Fixed
  there with `replace_text`: the blank line before it goes instead.
- `hand:` the tool description in `Tools.java` (a text block with `\`
  line ends) and the contract, by a Python replace. `replace_text` fit; I
  used Python to avoid escaping the backslashes in JSON. That was the
  wrong call for `Tools.java`, which is Java.

- Stress run (seed 2, 110 calls each on Lang and Math): no failures;
  `inline_function` applied twice on each, none of the new refusals came
  up on those targets. The gate still refuses `getInstance` methods that
  name a private `LazyHolder` from another class (compile error, not a
  behavior change).

## Measured

From `bin/session-report`:

- tool calls: 27
- through jrefactor: 4 refactoring steps (`add_parameter`), 28 edit steps
- refused by jrefactor: 0
- by hand: 3 (the record, the Python edit of `Tools.java` and the contract,
  the session note)
- large writes: 2 (the goldens batch, the helpers)
- reads: Bash java 4, inspect 3

## Assessment

- **Through the tools or around them:** through, except the description
  in `Tools.java`, a hand edit `replace_text` covered.
- **Large writes:** the helpers are new behavior; nothing existing was
  typed again.
- **Did the plan hold?** Yes: the goldens first (five red), then the rules
  in one batch; one extra fix (`removal`'s blank line) that the goldens
  showed.
- **Reading:** the bodies needed to place the new checks.
- **One change that would have helped:** none new. Writing a text block
  with `\` line ends through `replace_text` means escaping in JSON. A
  shell file argument (`new_file=`, as the CLI has) would avoid it on the
  server too; not logged, since it cost only care.

## Follow-ups

- The rules are conservative: a field read used once after a call the body
  makes (`g() + x` with `f(count)`) is refused even when `g` does not
  change `count`.
- A type named bare in the body could mean another type at a call in
  another package. Not handled; the gate catches only one that does not
  resolve.
- A private member of the method's class used in the body still fails at
  a call in another class (the gate refuses).
