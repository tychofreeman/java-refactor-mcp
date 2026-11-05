# Probing fold and inline_function's argument binding

- **Item:** behavior probes for `fold` (#103, #181) and `inline_function`'s argument
  binding (#102, #180), both new and broad. The session notes' testing rule: a tool
  that moves code needs probes for evaluation order and name capture, since the
  stress tests see only compile errors (probes found seven behavior changes that
  type-checked in the 2026-11-04 tools). The user's choice of 2026-11-05 ("Probe fold
  and binding").
- **Date:** 2026-11-05
- **Branch:** probe-fold-binding

## Plan

The question is whether either tool applies an edit that compiles and changes what
the program does. Each probe is a small program whose `main` prints what it did;
it is compiled and run before and after the tool, and the outputs compared. An
applied edit with a different output is a bug; a refusal or a left match is
recorded with its reason. The harness is a script in the scratch directory.

- `fold`: an argument the code evaluates twice; one in a branch, or right of
  `&&`, in the code; two arguments in another order, one a field the other changes;
  an overload the call would resolve to instead (`m(Object)` folded, `m(String)`
  beside it); a local the run declares and the block uses after; a generic method;
  the format relaxation with text that has `%`, quotes or regex characters.
- binding: a call in a loop condition, a `for` update, right of `&&`, a branch of
  `?:`, a lambda body; a name the method declares later; a parameter type that needs
  an import.

Each bug found gets a golden first, then the fix, one PR per tool if both have one.

## Results

21 probes, each a program run before and after the tool (the harness compiles
with `javac`, runs `main`, compares stdout).

`fold`, 11 probes:

| probe | result |
|---|---|
| argument evaluated twice (`next() + next()`) | left: evaluated 2 times |
| argument in a `?:` branch | left by the gate |
| argument right of `&&` | left by the gate |
| `x - g()`, `g()` changes `x` | left: another order |
| **`show(Object)` folded, `show(String)` beside it** | **applied, output changed** (`[x]` became `<x>`): fixed |
| local declared by the run, used after | left |
| generic method | applied, same output |
| format text with regex characters, quotes, `%%` | applied, same output |
| match in a nested class that has its own method of the name | applied (qualified), same output |
| argument read past an effect of the code | left by the gate |
| statements in a loop with `i++` twice | left: evaluated 2 times |

Binding, 10 probes: no behavior change. Refused, as it must be, where the bound
local would run at another time: a loop condition, a `for` update, right of `&&`,
a `?:` branch, a `switch` case, after an earlier effect of the statement, after an
array index on the left of `=`. Applied with the same output: in a lambda body (the
local inside it), with a name the method declares later (the local is `x2`), and
with a parameter type that needs an import.

## The fix

`fold` wrote `show(s)` for a match with a `String` argument; the call resolved to
the more specific `show(String)`. When the method's class or a supertype has another
method of the name and arity, a match folds only if each argument's type is its
parameter's own (erasure) and none is `null`: then the folded method is the one
chosen. Golden `fold_left_overload` (one match left, one folded).

## Notes

- `read:` none of the tools' bodies; the probes are black-box.
- The read guard (#185) refused a `sed` of a probe's output Java; read it with
  `bin/jrefactor inspect` instead, as it says.
- `hand:` none in the repository; the harness and probes are scratch files.

## Measured

From `bin/session-report`: 14 tool calls, 7 edit steps through jrefactor, no
refusals, nothing by hand; large writes: the check (`replace_text`, 32 lines) and
`overloaded` (`add_declaration`, 23 lines), both new code; scratch writes: the
harness and the two probe files.

## Assessment

- **Through the tools or around them:** through.
- **The probes earned their cost:** one behavior change that compiled, in `fold`,
  found before any model used it on real code; binding had none.
- **One change that would help:** keep the harness in the repository (it runs a
  program before and after any tool), so the next new tool gets probes by default;
  roadmap #16's stress tests see only compile errors.

## Follow-ups

- The probe harness as an opt-in test (`-Djrefactor.probes`), with these 21 probes.
